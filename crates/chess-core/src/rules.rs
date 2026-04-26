//! Draw-detection helpers (FIDE rules, [`data-model.md §5`]).
//!
//! - [`is_threefold`] — has the current Zobrist hash appeared 3+ times in the
//!   provided history?
//! - [`is_fifty_move`] — has 50 full moves elapsed without a pawn push or
//!   capture?
//! - [`is_insufficient_material`] — only K, K+B, K+N, or K+B vs. K+B with
//!   same-coloured bishops on each side?
//!
//! These helpers are used by [`crate::Game`] to detect terminal states; they
//! do NOT call the move generator.
//!
//! [`data-model.md §5`]: ../../../specs/001-chess-ai-rewrite/data-model.md

use std::collections::HashMap;

use crate::position::{Color, PieceType, Position};

/// True iff `pos.zobrist` has been visited 3 or more times in
/// `repetition_table` (FIDE 9.2.2 — threefold repetition).
pub fn is_threefold(repetition_table: &HashMap<u64, u8>, pos: &Position) -> bool {
    repetition_table
        .get(&pos.zobrist)
        .map(|&c| c >= 3)
        .unwrap_or(false)
}

/// True iff `pos.halfmove_clock >= 100` (FIDE 9.3 — 50-move rule).
///
/// 50 *full* moves means 100 *half-moves* without a pawn push or capture.
pub fn is_fifty_move(pos: &Position) -> bool {
    pos.halfmove_clock >= 100
}

/// True iff neither side has sufficient material to checkmate (FIDE 5.2.2 /
/// 9.6 — insufficient material draw).
///
/// Recognized configurations (per [`data-model.md §5`]):
/// - K vs. K
/// - K + B vs. K
/// - K + N vs. K
/// - K + B vs. K + B with both bishops on **the same colour squares**
pub fn is_insufficient_material(pos: &Position) -> bool {
    let total_pieces = pos.occupied().count();
    if total_pieces == 2 {
        // Just the two kings.
        return true;
    }
    if total_pieces > 4 {
        return false;
    }

    // No pawns / rooks / queens — they can always force mate.
    for color in [Color::White, Color::Black] {
        for kind in [PieceType::Pawn, PieceType::Rook, PieceType::Queen] {
            if !pos.pieces_of(color, kind).is_empty() {
                return false;
            }
        }
    }

    let w_n = pos.pieces_of(Color::White, PieceType::Knight).count();
    let b_n = pos.pieces_of(Color::Black, PieceType::Knight).count();
    let w_b = pos.pieces_of(Color::White, PieceType::Bishop).count();
    let b_b = pos.pieces_of(Color::Black, PieceType::Bishop).count();

    let w_minor = w_n + w_b;
    let b_minor = b_n + b_b;

    // K + minor vs. K — draw.
    if total_pieces == 3 && (w_minor + b_minor == 1) {
        return true;
    }
    // K + B vs. K + B with same-colour bishops — draw.
    if total_pieces == 4 && w_b == 1 && b_b == 1 && w_n == 0 && b_n == 0 {
        let w_bishop_sq = pos
            .pieces_of(Color::White, PieceType::Bishop)
            .lsb()
            .expect("bishop count == 1");
        let b_bishop_sq = pos
            .pieces_of(Color::Black, PieceType::Bishop)
            .lsb()
            .expect("bishop count == 1");
        let w_color_dark = (w_bishop_sq.file() + w_bishop_sq.rank()) % 2 == 0;
        let b_color_dark = (b_bishop_sq.file() + b_bishop_sq.rank()) % 2 == 0;
        if w_color_dark == b_color_dark {
            return true;
        }
    }

    // Two knights vs. king is *technically* a draw against best defence, but
    // FIDE treats it as not insufficient material because mate is reachable
    // with co-operation. Per spec we follow the strict "K vs K, KB vs K, KN
    // vs K, KBB-same-colour" set, so two knights returns false here.
    false
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::position::Position;

    #[test]
    fn kk_only_is_insufficient() {
        let p = Position::from_fen("4k3/8/8/8/8/8/8/4K3 w - - 0 1").unwrap();
        assert!(is_insufficient_material(&p));
    }

    #[test]
    fn kbk_is_insufficient() {
        let p = Position::from_fen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1").unwrap();
        assert!(is_insufficient_material(&p));
    }

    #[test]
    fn knk_is_insufficient() {
        let p = Position::from_fen("4k3/8/8/8/8/8/8/4KN2 w - - 0 1").unwrap();
        assert!(is_insufficient_material(&p));
    }

    #[test]
    fn kqk_is_sufficient() {
        let p = Position::from_fen("4k3/8/8/8/8/8/8/4KQ2 w - - 0 1").unwrap();
        assert!(!is_insufficient_material(&p));
    }

    #[test]
    fn pawns_present_is_sufficient() {
        let p = Position::from_fen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1").unwrap();
        assert!(!is_insufficient_material(&p));
    }

    #[test]
    fn kbk_b_same_colour_bishops_is_insufficient() {
        // White king e1, white bishop c1 (file 2 + rank 0 = 2, dark).
        // Black king e8, black bishop b8 (file 1 + rank 7 = 8, dark).
        // K+B vs. K+B with same-colour bishops -> draw.
        let p = Position::from_fen("1b2k3/8/8/8/8/8/8/2B1K3 w - - 0 1").unwrap();
        assert!(is_insufficient_material(&p));
    }

    #[test]
    fn kbk_b_opposite_colour_bishops_is_sufficient() {
        // White bishop c1 (dark), black bishop c8 (light) -> sufficient.
        let p = Position::from_fen("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1").unwrap();
        assert!(!is_insufficient_material(&p));
    }

    #[test]
    fn fifty_move_threshold() {
        let mut p = Position::from_fen("4k3/8/8/8/8/8/8/4K3 w - - 99 50").unwrap();
        assert!(!is_fifty_move(&p));
        p.halfmove_clock = 100;
        assert!(is_fifty_move(&p));
    }
}
