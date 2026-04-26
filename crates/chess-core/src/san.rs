//! Standard Algebraic Notation (SAN) render and parse.
//!
//! - [`san_for`] — render a [`Move`] in the canonical FIDE SAN form (e.g.
//!   `O-O`, `Nbd2`, `exd5`, `e8=Q+`, `bxa6 e.p.`).
//! - [`parse_san`] — find the unique legal [`Move`] in `pos` that matches the
//!   given SAN string.
//!
//! Implementation note: parsing is implemented as "render every legal move
//! and compare strings." This is O(N) per parse but completely correct and
//! comfortably fast for any UI use-case (legal-move counts top out around
//! 218 moves in pathological positions; typical positions have ~30-40).

use thiserror::Error;

use crate::movegen::{is_in_check, legal_moves};
use crate::moves::{Move, MoveFlag, Promotion};
use crate::position::{PieceType, Position, Square};

// ---------------------------------------------------------------------------
// Render
// ---------------------------------------------------------------------------

/// Render `mv` in canonical SAN for `pos`. Caller must ensure `mv` is one of
/// the legal moves of `pos`; the function does not re-validate but it does
/// rely on `legal_moves(pos)` to compute disambiguation correctly.
pub fn san_for(pos: &Position, mv: Move) -> String {
    if mv.is_castle() {
        // King-side: to-file == g (6). Queen-side: to-file == c (2).
        let body = if mv.to().file() == 6 { "O-O" } else { "O-O-O" };
        return body.to_string() + &check_or_mate_suffix(pos, mv);
    }

    let from = mv.from();
    let to = mv.to();
    let stm = pos.side_to_move;
    let moving = pos
        .piece_on(from)
        .expect("san_for: no piece on `from` square");

    let mut s = String::with_capacity(8);

    if moving.kind == PieceType::Pawn {
        if mv.is_capture() {
            s.push((b'a' + from.file()) as char);
            s.push('x');
        }
        s.push_str(&to.algebraic());
        if let Some(p) = promotion_letter(mv.promotion()) {
            s.push('=');
            s.push(p);
        }
        if mv.flag() == MoveFlag::EnPassant {
            s.push_str(" e.p.");
        }
    } else {
        s.push(piece_letter(moving.kind));
        // Disambiguation.
        let (need_file, need_rank) = disambiguation_needed(pos, mv);
        if need_file {
            s.push((b'a' + from.file()) as char);
        }
        if need_rank {
            s.push((b'1' + from.rank()) as char);
        }
        if mv.is_capture() {
            s.push('x');
        }
        s.push_str(&to.algebraic());
    }

    s.push_str(&check_or_mate_suffix(pos, mv));
    let _ = stm;
    s
}

fn promotion_letter(p: Promotion) -> Option<char> {
    match p {
        Promotion::None => None,
        Promotion::Knight => Some('N'),
        Promotion::Bishop => Some('B'),
        Promotion::Rook => Some('R'),
        Promotion::Queen => Some('Q'),
    }
}

fn piece_letter(k: PieceType) -> char {
    match k {
        PieceType::Pawn => 'P', // not actually used in SAN output
        PieceType::Knight => 'N',
        PieceType::Bishop => 'B',
        PieceType::Rook => 'R',
        PieceType::Queen => 'Q',
        PieceType::King => 'K',
    }
}

fn disambiguation_needed(pos: &Position, mv: Move) -> (bool, bool) {
    let from = mv.from();
    let to = mv.to();
    let moving = pos
        .piece_on(from)
        .expect("disambiguation: no piece on `from`");
    let kind = moving.kind;
    let mut conflicts: Vec<Square> = Vec::new();
    for other in legal_moves(pos).into_iter() {
        if other == mv {
            continue;
        }
        if other.to() != to {
            continue;
        }
        let piece = match pos.piece_on(other.from()) {
            Some(p) => p,
            None => continue,
        };
        if piece.kind == kind {
            conflicts.push(other.from());
        }
    }

    if conflicts.is_empty() {
        return (false, false);
    }
    let same_file = conflicts.iter().any(|s| s.file() == from.file());
    let same_rank = conflicts.iter().any(|s| s.rank() == from.rank());
    if !same_file {
        // File alone disambiguates.
        (true, false)
    } else if !same_rank {
        // Rank alone disambiguates.
        (false, true)
    } else {
        (true, true)
    }
}

fn check_or_mate_suffix(pos: &Position, mv: Move) -> String {
    let (after, _) = pos.make_move(mv);
    if !is_in_check(&after) {
        return String::new();
    }
    if legal_moves(&after).is_empty() {
        "#".to_string()
    } else {
        "+".to_string()
    }
}

// ---------------------------------------------------------------------------
// Parse
// ---------------------------------------------------------------------------

/// Errors returned by [`parse_san`].
#[derive(Error, Debug, PartialEq, Eq)]
pub enum SanError {
    /// No legal move in the position matches the given SAN string.
    #[error("no legal move matches SAN {san:?}")]
    NoMatch {
        /// Offending SAN.
        san: String,
    },
    /// More than one legal move in the position matches (this should be
    /// impossible for canonical SAN, but is reported defensively).
    #[error("ambiguous SAN {san:?}: matches {count} legal moves")]
    Ambiguous {
        /// Offending SAN.
        san: String,
        /// How many legal moves matched.
        count: usize,
    },
}

/// Find the unique legal move in `pos` matching `san`.
///
/// `san` is normalised before matching (whitespace stripped, "+" / "#"
/// suffixes ignored) so callers that produce minor variations
/// (e.g. omitting the check marker) are tolerated.
pub fn parse_san(pos: &Position, san: &str) -> Result<Move, SanError> {
    let target = normalise(san);
    let mut matches: Vec<Move> = Vec::new();
    for mv in legal_moves(pos).into_iter() {
        let rendered = normalise(&san_for(pos, mv));
        if rendered == target {
            matches.push(mv);
        }
    }
    match matches.len() {
        0 => Err(SanError::NoMatch {
            san: san.to_string(),
        }),
        1 => Ok(matches[0]),
        n => Err(SanError::Ambiguous {
            san: san.to_string(),
            count: n,
        }),
    }
}

fn normalise(s: &str) -> String {
    let trimmed = s.trim();
    let no_check = trimmed.trim_end_matches(['+', '#']);
    let no_ep = no_check.replace(" e.p.", "").replace("e.p.", "");
    no_ep.replace(['+', '#'], "")
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::position::Position;

    #[test]
    fn render_simple_pawn_pushes() {
        let p = Position::startpos();
        let e4 = parse_san(&p, "e4").expect("e4 is legal from startpos");
        assert_eq!(san_for(&p, e4), "e4");

        let nf3 = parse_san(&p, "Nf3").expect("Nf3 is legal");
        assert_eq!(san_for(&p, nf3), "Nf3");
    }

    #[test]
    fn render_castling_kingside() {
        let p = Position::from_fen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1").unwrap();
        // Cannot castle yet because the path from e1 to h1 has Bg/N pieces in
        // initial position; here the rank-1 is empty between king and rook.
        let oo = parse_san(&p, "O-O").expect("O-O is legal");
        assert_eq!(san_for(&p, oo), "O-O");
    }

    #[test]
    fn render_disambiguation_by_file() {
        // White king e1, white knights b1 and d1, black king e8.
        // Both knights b1 and d1 can move to c3 -> file disambiguation.
        let p = Position::from_fen("4k3/8/8/8/8/8/8/1N1NK3 w - - 0 1").expect("valid FEN");
        let nbc3 = parse_san(&p, "Nbc3").expect("Nbc3 should be legal");
        assert_eq!(san_for(&p, nbc3), "Nbc3");
        let ndc3 = parse_san(&p, "Ndc3").expect("Ndc3 should be legal");
        assert_eq!(san_for(&p, ndc3), "Ndc3");
    }

    #[test]
    fn parse_ignores_check_marker() {
        let p = Position::from_fen("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1").unwrap();
        // White rook on a1 moves to e1? No, king is on e1. Use Ra5+? Black king
        // on e8 — Ra5 doesn't check. Use Re1 — but king is on e1. Pick a
        // legal-and-checking move for this contrived FEN: white rook a1 to a8
        // doesn't check either. Just verify the mechanism: pick a non-checking
        // move and assert the parser tolerates a stray "+".
        let mv = parse_san(&p, "Ra2").expect("Ra2 legal");
        let mv2 = parse_san(&p, "Ra2+").expect("Ra2+ should normalise");
        assert_eq!(mv, mv2);
    }
}
