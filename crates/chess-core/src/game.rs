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
}
