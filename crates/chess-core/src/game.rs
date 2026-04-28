//! [`Game`] — the in-memory chess game state machine
//! (per [`data-model.md §4-§5`]).
//!
//! - Tracks the start position, the current position, and the full move
//!   history (`MoveRecord` per ply).
//! - Maintains a Zobrist-keyed repetition table for FIDE 9.2.2 detection.
//! - Records terminal state (`GameResult`) once detected.
//! - Supports unlimited [`Game::undo`] / [`Game::redo`] (FR-014).
//!
//! The [`Game`] struct is a pure data structure — it owns no IO, no engine
//! handles, and no UI state.
//!
//! [`data-model.md §4-§5`]: ../../../specs/001-chess-ai-rewrite/data-model.md

use std::collections::HashMap;

use crate::movegen::{is_in_check, legal_moves};
use crate::moves::{Move, MoveRecord};
use crate::position::{Color, Position};
use crate::rules::{is_fifty_move, is_insufficient_material, is_threefold};
use crate::san::san_for;

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

/// Who is playing each side.
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum GameMode {
    /// Human vs. AI; the contained `Color` is the human.
    HumanVsAi(Color),
    /// AI vs. AI demo / tournament mode.
    AiVsAi,
}

/// Reason for a draw (FIDE).
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum DrawReason {
    /// Side to move has no legal move and is not in check.
    Stalemate,
    /// 50 full moves elapsed without a pawn push or capture.
    FiftyMoveRule,
    /// Same position with same side-to-move and rights occurred 3+ times.
    ThreefoldRepetition,
    /// Mating material is insufficient for either side.
    InsufficientMaterial,
    /// Both players agreed (not exposed in the v1 UI; accessible via API).
    AgreedDraw,
}

/// Terminal state of a game.
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum GameResult {
    /// `Color` = winner (the side that delivered mate).
    Checkmate(Color),
    /// `Color` = winner; loser resigned.
    Resignation(Color),
    /// Drawn game.
    Draw(DrawReason),
}

// ---------------------------------------------------------------------------
// Game
// ---------------------------------------------------------------------------

/// A complete in-memory chess game.
#[derive(Clone, Debug)]
pub struct Game {
    /// Starting position (always [`Position::startpos`] in v1; FR-004).
    pub start_position: Position,
    /// Position after applying every entry in `history`.
    pub current: Position,
    /// All moves played from `start_position`, in order.
    pub history: Vec<MoveRecord>,
    /// Records popped by `undo`; cleared whenever a fresh move is played.
    pub redo_stack: Vec<MoveRecord>,
    /// Zobrist → count, for threefold detection.
    pub repetition_table: HashMap<u64, u8>,
    /// Terminal state, once detected.
    pub result: Option<GameResult>,
    /// Who is playing each side.
    pub mode: GameMode,
}

impl Game {
    /// Start a fresh game from the standard opening position.
    pub fn new_game(mode: GameMode) -> Self {
        let start = Position::startpos();
        let mut rep = HashMap::new();
        rep.insert(start.zobrist, 1u8);
        let mut g = Game {
            start_position: start,
            current: start,
            history: Vec::new(),
            redo_stack: Vec::new(),
            repetition_table: rep,
            result: None,
            mode,
        };
        g.detect_result();
        g
    }

    /// Apply `mv` (which MUST be a member of `legal_moves(&self.current)`).
    ///
    /// Updates `current`, `history`, `repetition_table`, and `result`. Clears
    /// `redo_stack`. Returns an error if the move is illegal in the current
    /// position OR if the game is already terminated.
    pub fn make_move(&mut self, mv: Move) -> Result<(), MoveError> {
        if self.result.is_some() {
            return Err(MoveError::GameOver);
        }
        let legal = legal_moves(&self.current);
        if !legal.contains(&mv) {
            return Err(MoveError::Illegal);
        }
        let san = san_for(&self.current, mv);
        let (after, mut record) = self.current.make_move(mv);
        record.san = san;

        self.current = after;
        self.history.push(record);
        self.redo_stack.clear();

        let counter = self.repetition_table.entry(after.zobrist).or_insert(0);
        *counter = counter.saturating_add(1);

        self.detect_result();
        Ok(())
    }

    /// Reverse the most recently played move. Pushes the popped record onto
    /// `redo_stack` so [`Game::redo`] can restore it.
    pub fn undo(&mut self) -> Result<(), MoveError> {
        let record = self.history.pop().ok_or(MoveError::NothingToUndo)?;
        // Decrement repetition counter for the position we're leaving.
        if let Some(counter) = self.repetition_table.get_mut(&self.current.zobrist) {
            *counter = counter.saturating_sub(1);
            if *counter == 0 {
                self.repetition_table.remove(&self.current.zobrist);
            }
        }
        self.current = self.current.unmake_move(&record);
        self.redo_stack.push(record);
        // Result might have been set by the just-undone move; clear and
        // re-detect (the game might still be over for *another* reason — e.g.
        // 50-move rule — but typically not).
        self.result = None;
        self.detect_result();
        Ok(())
    }

    /// Re-apply the most recently undone move.
    pub fn redo(&mut self) -> Result<(), MoveError> {
        let record = self.redo_stack.pop().ok_or(MoveError::NothingToRedo)?;
        let mv = record.move_played;
        let (after, mut new_record) = self.current.make_move(mv);
        new_record.san = record.san.clone();

        self.current = after;
        self.history.push(new_record);

        let counter = self.repetition_table.entry(after.zobrist).or_insert(0);
        *counter = counter.saturating_add(1);

        self.detect_result();
        Ok(())
    }

    /// Convenience accessor for the terminal state.
    #[inline]
    pub fn result(&self) -> Option<GameResult> {
        self.result
    }

    /// Current side-to-move (delegates to the underlying [`Position`]).
    #[inline]
    pub fn side_to_move(&self) -> Color {
        self.current.side_to_move
    }

    fn detect_result(&mut self) {
        if self.result.is_some() {
            return;
        }
        let legal = legal_moves(&self.current);
        if legal.is_empty() {
            if is_in_check(&self.current) {
                // The side-to-move has no legal moves and is in check.
                // Mate was delivered by the other side.
                self.result = Some(GameResult::Checkmate(self.current.side_to_move.opp()));
            } else {
                self.result = Some(GameResult::Draw(DrawReason::Stalemate));
            }
            return;
        }
        if is_threefold(&self.repetition_table, &self.current) {
            self.result = Some(GameResult::Draw(DrawReason::ThreefoldRepetition));
            return;
        }
        if is_fifty_move(&self.current) {
            self.result = Some(GameResult::Draw(DrawReason::FiftyMoveRule));
            return;
        }
        if is_insufficient_material(&self.current) {
            self.result = Some(GameResult::Draw(DrawReason::InsufficientMaterial));
        }
    }
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

/// Errors returned by [`Game`] mutators.
#[derive(thiserror::Error, Debug, PartialEq, Eq)]
pub enum MoveError {
    /// Move is not a legal move in the current position.
    #[error("move is not legal in the current position")]
    Illegal,
    /// Game is already over.
    #[error("game is already over")]
    GameOver,
    /// `undo` called with no moves played.
    #[error("nothing to undo")]
    NothingToUndo,
    /// `redo` called with empty redo stack.
    #[error("nothing to redo")]
    NothingToRedo,
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::san::parse_san;

    #[test]
    fn new_game_starts_in_progress() {
        let g = Game::new_game(GameMode::HumanVsAi(Color::White));
        assert!(g.result.is_none());
        assert_eq!(g.history.len(), 0);
        assert_eq!(g.repetition_table.get(&g.current.zobrist), Some(&1));
    }

    #[test]
    fn fools_mate_detected_as_checkmate() {
        // 1. f3 e5 2. g4 Qh4#
        let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
        for san in ["f3", "e5", "g4", "Qh4#"] {
            let mv = parse_san(&g.current, san).expect(san);
            g.make_move(mv).expect(san);
        }
        assert_eq!(g.result, Some(GameResult::Checkmate(Color::Black)));
    }

    #[test]
    fn undo_redo_round_trips() {
        let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
        let e4 = parse_san(&g.current, "e4").unwrap();
        g.make_move(e4).unwrap();
        let after_e4 = g.current;
        g.undo().unwrap();
        assert_eq!(g.current, g.start_position);
        g.redo().unwrap();
        assert_eq!(g.current, after_e4);
    }

    #[test]
    fn cannot_play_after_mate() {
        let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
        for san in ["f3", "e5", "g4", "Qh4#"] {
            let mv = parse_san(&g.current, san).unwrap();
            g.make_move(mv).unwrap();
        }
        // Try to play any legal-looking move (which would be irrelevant since
        // game is over). We have no legal moves anyway, but the test checks
        // the GameOver gate.
        let pseudo = Move::NULL;
        assert_eq!(g.make_move(pseudo), Err(MoveError::GameOver));
    }

    // -----------------------------------------------------------------
    // T068: undo/redo round-trip across every special move kind.
    //
    // Spec (specs/001-chess-ai-rewrite/tasks.md, T068):
    //   "Undo/Redo round-trip across every special-move kind:
    //    castling (KS + QS), en-passant (white & black), promotion
    //    (Q/R/B/N), promotion-with-capture-with-check. For each,
    //    assert position == undo(make_move(position, m)) bit-for-bit
    //    (including Zobrist)."
    //
    // The helper plays a sequence of SAN moves to reach a setup
    // position, then asserts that performing one more move and
    // immediately undoing it returns the position byte-for-byte
    // (including Zobrist + repetition counters), and that redo
    // restores the post-move position byte-for-byte.
    // -----------------------------------------------------------------

    fn assert_undo_redo_round_trip(setup: &[&str], target: &str) {
        let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
        for san in setup {
            let mv = parse_san(&g.current, san)
                .unwrap_or_else(|e| panic!("setup move '{san}' failed: {e:?}"));
            g.make_move(mv)
                .unwrap_or_else(|e| panic!("setup play '{san}' failed: {e:?}"));
        }

        let before = g.current.clone();
        let before_zobrist = before.zobrist;
        let history_len_before = g.history.len();

        let mv = parse_san(&g.current, target)
            .unwrap_or_else(|e| panic!("target '{target}' parse failed: {e:?}"));
        g.make_move(mv)
            .unwrap_or_else(|e| panic!("target '{target}' play failed: {e:?}"));
        let after = g.current.clone();
        let after_zobrist = after.zobrist;

        // Undo brings us back exactly.
        g.undo().expect("undo");
        assert_eq!(
            g.current, before,
            "[{target}] undo did not restore position bit-for-bit"
        );
        assert_eq!(
            g.current.zobrist, before_zobrist,
            "[{target}] undo did not restore Zobrist hash"
        );
        assert_eq!(g.history.len(), history_len_before);

        // Redo restores the post-move position exactly.
        g.redo().expect("redo");
        assert_eq!(
            g.current, after,
            "[{target}] redo did not restore position bit-for-bit"
        );
        assert_eq!(
            g.current.zobrist, after_zobrist,
            "[{target}] redo did not restore Zobrist hash"
        );
        assert_eq!(g.history.len(), history_len_before + 1);
    }

    #[test]
    fn undo_redo_round_trip_kingside_castle_white() {
        // Standard Italian opening clearing the kingside.
        assert_undo_redo_round_trip(
            &["e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5"],
            "O-O",
        );
    }

    #[test]
    fn undo_redo_round_trip_queenside_castle_white() {
        // Reach a position where White can O-O-O. Queen's-Pawn opening
        // with quick development of queenside minor pieces and queen.
        assert_undo_redo_round_trip(
            &["d4", "d5", "Nc3", "Nf6", "Bf4", "Bf5", "Qd2", "Qd7"],
            "O-O-O",
        );
    }

    #[test]
    fn undo_redo_round_trip_en_passant_white_captures() {
        // 1.e4 a6 2.e5 d5 — now White can play exd6 e.p.
        assert_undo_redo_round_trip(
            &["e4", "a6", "e5", "d5"],
            "exd6",
        );
    }

    #[test]
    fn undo_redo_round_trip_en_passant_black_captures() {
        // Reach a position where Black can capture en passant.
        // 1.Nf3 e5 2.Nc3 e4 3.d4 — Black plays exd3 e.p.
        assert_undo_redo_round_trip(
            &["Nf3", "e5", "Nc3", "e4", "d4"],
            "exd3",
        );
    }

    /// Promotion-test setup: lands a White pawn on e7 with the d-file
    /// 8th-rank target (Black queen on d8) reachable by a capturing
    /// promotion `exd8=X`. Reached via:
    ///
    /// 1. e4   d5
    /// 2. exd5 f6      (clears the d-pawn out of White's way; Black
    ///                  passes time)
    /// 3. d6   c6      (push toward 7th)
    /// 4. dxe7 c5      (White pawn now on e7, Black has spent moves
    ///                  on the queenside; Black queen still on d8)
    ///
    /// Black king is on e8, queen on d8; promoting `exd8=Q` or `=R`
    /// delivers check (adjacent on the 8th rank), `=B` and `=N` do
    /// not (those pieces don't attack e8 from d8). All four are
    /// legal promotion-with-capture round-trip targets.
    fn promotion_setup_capture_d8() -> Vec<&'static str> {
        vec!["e4", "d5", "exd5", "f6", "d6", "c6", "dxe7", "c5"]
    }

    #[test]
    fn undo_redo_round_trip_promotion_to_queen() {
        assert_undo_redo_round_trip(&promotion_setup_capture_d8(), "exd8=Q");
    }

    #[test]
    fn undo_redo_round_trip_promotion_to_rook() {
        assert_undo_redo_round_trip(&promotion_setup_capture_d8(), "exd8=R");
    }

    #[test]
    fn undo_redo_round_trip_promotion_to_bishop() {
        assert_undo_redo_round_trip(&promotion_setup_capture_d8(), "exd8=B");
    }

    #[test]
    fn undo_redo_round_trip_promotion_to_knight() {
        assert_undo_redo_round_trip(&promotion_setup_capture_d8(), "exd8=N");
    }

    #[test]
    fn undo_redo_round_trip_promotion_with_capture_with_check() {
        // exd8=Q+ : pawn on e7 captures Black queen on d8 promoting
        // to a queen, which gives check on the Black king on e8
        // (adjacent on the 8th rank). This is the promotion + capture
        // + check combination called out in T068.
        assert_undo_redo_round_trip(&promotion_setup_capture_d8(), "exd8=Q+");
    }
}
