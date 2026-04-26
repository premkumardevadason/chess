//! Move generation for [`crate::Position`].
//!
//! This module produces *legal* moves: it enumerates pseudo-legal moves and
//! filters out any move that would leave the moving side's king in check.
//! Castling additionally requires the king to be out of check on its starting,
//! intermediate, and final squares.
//!
//! ## Algorithm
//!
//! v1.0 uses **classical bitboards** with on-the-fly ray scanning for sliding
//! pieces and precomputed attack tables for knights, kings, and pawns. This is
//! correct and small; the magic-bitboard refactor (with packed pre-computed
//! tables ported from the forked Carp engine) is scheduled for the engine
//! library — see `tasks.md` T016 / CP-C.
//!
//! ## Public API
//!
//! - [`legal_moves`] — the canonical move list for a position.
//! - [`is_in_check`] — convenience wrapper for `is_square_attacked` against
//!   the side-to-move's king.
//! - [`is_square_attacked`] — used by `legal_moves`, `is_in_check`, and the
//!   game state machine.

use smallvec::SmallVec;

use crate::moves::{Move, MoveList, Promotion};
use crate::position::{Bitboard, CastlingRights, Color, PieceType, Position, Square, FILES, RANKS};

// ---------------------------------------------------------------------------
// Precomputed attack tables (lazy)
// ---------------------------------------------------------------------------

struct AttackTables {
    knight: [Bitboard; 64],
    king: [Bitboard; 64],
    /// `pawn[color][sq]` = squares attacked **by** a pawn of `color` sitting
    /// on `sq`. Indexed by [`Color::index`].
    pawn: [[Bitboard; 64]; 2],
}

fn tables() -> &'static AttackTables {
    static TABLES: std::sync::OnceLock<AttackTables> = std::sync::OnceLock::new();
    TABLES.get_or_init(|| {
        let mut knight = [Bitboard::EMPTY; 64];
        let mut king = [Bitboard::EMPTY; 64];
        let mut pawn = [[Bitboard::EMPTY; 64]; 2];

        let knight_dirs: [(i8, i8); 8] = [
            (1, 2),
            (2, 1),
            (-1, 2),
            (-2, 1),
            (1, -2),
            (2, -1),
            (-1, -2),
            (-2, -1),
        ];
        let king_dirs: [(i8, i8); 8] = [
            (1, 0),
            (-1, 0),
            (0, 1),
            (0, -1),
            (1, 1),
            (1, -1),
            (-1, 1),
            (-1, -1),
        ];

        for sq_idx in 0..64u8 {
            let sq = Square::new(sq_idx);
            let f = sq.file() as i8;
            let r = sq.rank() as i8;

            for (df, dr) in knight_dirs {
                let nf = f + df;
                let nr = r + dr;
                if (0..8).contains(&nf) && (0..8).contains(&nr) {
                    knight[sq_idx as usize] =
                        knight[sq_idx as usize].with(Square::from_file_rank(nf as u8, nr as u8));
                }
            }
            for (df, dr) in king_dirs {
                let nf = f + df;
                let nr = r + dr;
                if (0..8).contains(&nf) && (0..8).contains(&nr) {
                    king[sq_idx as usize] =
                        king[sq_idx as usize].with(Square::from_file_rank(nf as u8, nr as u8));
                }
            }
            // White pawn attacks: up-left (+7, file>0) and up-right (+9, file<7).
            if r < 7 {
                if f > 0 {
                    pawn[0][sq_idx as usize] = pawn[0][sq_idx as usize]
                        .with(Square::from_file_rank((f - 1) as u8, (r + 1) as u8));
                }
                if f < 7 {
                    pawn[0][sq_idx as usize] = pawn[0][sq_idx as usize]
                        .with(Square::from_file_rank((f + 1) as u8, (r + 1) as u8));
                }
            }
            // Black pawn attacks: down-left (-9, file>0) and down-right (-7, file<7).
            if r > 0 {
                if f > 0 {
                    pawn[1][sq_idx as usize] = pawn[1][sq_idx as usize]
                        .with(Square::from_file_rank((f - 1) as u8, (r - 1) as u8));
                }
                if f < 7 {
                    pawn[1][sq_idx as usize] = pawn[1][sq_idx as usize]
                        .with(Square::from_file_rank((f + 1) as u8, (r - 1) as u8));
                }
            }
        }

        AttackTables { knight, king, pawn }
    })
}

// ---------------------------------------------------------------------------
// Sliding attacks (ray scan)
// ---------------------------------------------------------------------------

const ROOK_DIRS: [(i8, i8); 4] = [(0, 1), (0, -1), (1, 0), (-1, 0)];
const BISHOP_DIRS: [(i8, i8); 4] = [(1, 1), (1, -1), (-1, 1), (-1, -1)];

#[inline]
fn sliding_attacks(sq: Square, occupied: Bitboard, dirs: &[(i8, i8)]) -> Bitboard {
    let mut bb = Bitboard::EMPTY;
    let from_file = sq.file() as i8;
    let from_rank = sq.rank() as i8;
    for &(df, dr) in dirs {
        let mut f = from_file + df;
        let mut r = from_rank + dr;
        while (0..8).contains(&f) && (0..8).contains(&r) {
            let to = Square::from_file_rank(f as u8, r as u8);
            bb = bb.with(to);
            if occupied.contains(to) {
                break;
            }
            f += df;
            r += dr;
        }
    }
    bb
}

#[inline]
fn rook_attacks(sq: Square, occupied: Bitboard) -> Bitboard {
    sliding_attacks(sq, occupied, &ROOK_DIRS)
}

#[inline]
fn bishop_attacks(sq: Square, occupied: Bitboard) -> Bitboard {
    sliding_attacks(sq, occupied, &BISHOP_DIRS)
}

#[inline]
fn queen_attacks(sq: Square, occupied: Bitboard) -> Bitboard {
    rook_attacks(sq, occupied) | bishop_attacks(sq, occupied)
}

// ---------------------------------------------------------------------------
// Public predicates
// ---------------------------------------------------------------------------

/// True iff `target` is attacked by any piece of `attacker` on `pos`.
///
/// This does **not** care whose turn it is; the caller chooses `attacker`.
/// The board's `occupied()` is used as the blocker mask for sliding pieces;
/// note that the target square itself may or may not contain a piece, which
/// is irrelevant for the attack test (we ask "would a piece at `target` be
/// attacked?").
pub fn is_square_attacked(pos: &Position, target: Square, attacker: Color) -> bool {
    let t = tables();
    let occ = pos.occupied();

    // Pawn attacks: a pawn of `attacker` attacks `target` iff a pawn of the
    // OPPOSITE colour sitting on `target` would attack squares occupied by
    // `attacker` pawns.
    let opp = attacker.opp();
    if !(t.pawn[opp.index()][target.index()] & pos.pieces_of(attacker, PieceType::Pawn)).is_empty()
    {
        return true;
    }
    if !(t.knight[target.index()] & pos.pieces_of(attacker, PieceType::Knight)).is_empty() {
        return true;
    }
    if !(t.king[target.index()] & pos.pieces_of(attacker, PieceType::King)).is_empty() {
        return true;
    }
    let bishops_queens =
        pos.pieces_of(attacker, PieceType::Bishop) | pos.pieces_of(attacker, PieceType::Queen);
    if !(bishop_attacks(target, occ) & bishops_queens).is_empty() {
        return true;
    }
    let rooks_queens =
        pos.pieces_of(attacker, PieceType::Rook) | pos.pieces_of(attacker, PieceType::Queen);
    if !(rook_attacks(target, occ) & rooks_queens).is_empty() {
        return true;
    }
    false
}

/// True iff the side-to-move's king is currently in check.
pub fn is_in_check(pos: &Position) -> bool {
    let stm = pos.side_to_move;
    let king_sq = pos.king_square(stm);
    is_square_attacked(pos, king_sq, stm.opp())
}

// ---------------------------------------------------------------------------
// Pseudo-legal generation
// ---------------------------------------------------------------------------

fn add_pawn_moves(out: &mut MoveList, pos: &Position, from: Square, to: Square, is_capture: bool) {
    let stm = pos.side_to_move;
    let promo_rank = match stm {
        Color::White => 7,
        Color::Black => 0,
    };
    if to.rank() == promo_rank {
        for promo in [
            Promotion::Queen,
            Promotion::Rook,
            Promotion::Bishop,
            Promotion::Knight,
        ] {
            out.push(Move::new_promotion(from, to, promo, is_capture));
        }
    } else if is_capture {
        out.push(Move::new_capture(from, to));
    } else {
        out.push(Move::new_quiet(from, to));
    }
}

fn pseudo_legal_pawn_moves(out: &mut MoveList, pos: &Position) {
    let stm = pos.side_to_move;
    let pawns = pos.pieces_of(stm, PieceType::Pawn);
    let occ = pos.occupied();
    let opp_occ = pos.occupied_by(stm.opp());
    let empty = !occ;

    // Single-push: shift up (white) or down (black).
    let single_push = match stm {
        Color::White => Bitboard::new(pawns.raw() << 8) & empty,
        Color::Black => Bitboard::new(pawns.raw() >> 8) & empty,
    };
    {
        let mut bb = single_push;
        while let Some(to) = bb.pop_lsb() {
            let from = match stm {
                Color::White => Square::new(to.raw() - 8),
                Color::Black => Square::new(to.raw() + 8),
            };
            add_pawn_moves(out, pos, from, to, false);
        }
    }

    // Double-push: must come from rank 2 (white) / rank 7 (black) with the
    // intermediate square already empty.
    let double_push = match stm {
        Color::White => Bitboard::new((single_push & RANKS[2]).raw() << 8) & empty,
        Color::Black => Bitboard::new((single_push & RANKS[5]).raw() >> 8) & empty,
    };
    {
        let mut bb = double_push;
        while let Some(to) = bb.pop_lsb() {
            let from = match stm {
                Color::White => Square::new(to.raw() - 16),
                Color::Black => Square::new(to.raw() + 16),
            };
            out.push(Move::new_quiet(from, to));
        }
    }

    // Captures.
    let cap_left = match stm {
        Color::White => Bitboard::new((pawns & !FILES[0]).raw() << 7) & opp_occ,
        Color::Black => Bitboard::new((pawns & !FILES[0]).raw() >> 9) & opp_occ,
    };
    let cap_right = match stm {
        Color::White => Bitboard::new((pawns & !FILES[7]).raw() << 9) & opp_occ,
        Color::Black => Bitboard::new((pawns & !FILES[7]).raw() >> 7) & opp_occ,
    };
    {
        let mut bb = cap_left;
        while let Some(to) = bb.pop_lsb() {
            let from = match stm {
                Color::White => Square::new(to.raw() - 7),
                Color::Black => Square::new(to.raw() + 9),
            };
            add_pawn_moves(out, pos, from, to, true);
        }
    }
    {
        let mut bb = cap_right;
        while let Some(to) = bb.pop_lsb() {
            let from = match stm {
                Color::White => Square::new(to.raw() - 9),
                Color::Black => Square::new(to.raw() + 7),
            };
            add_pawn_moves(out, pos, from, to, true);
        }
    }

    // En passant.
    if let Some(ep) = pos.en_passant {
        let attackers = tables().pawn[stm.opp().index()][ep.index()] & pawns;
        let mut bb = attackers;
        while let Some(from) = bb.pop_lsb() {
            out.push(Move::new_en_passant(from, ep));
        }
    }
}

fn add_piece_moves_from_attacks(
    out: &mut MoveList,
    pos: &Position,
    from: Square,
    attacks: Bitboard,
) {
    let stm = pos.side_to_move;
    let own_occ = pos.occupied_by(stm);
    let opp_occ = pos.occupied_by(stm.opp());
    let mut bb = attacks & !own_occ;
    while let Some(to) = bb.pop_lsb() {
        if opp_occ.contains(to) {
            out.push(Move::new_capture(from, to));
        } else {
            out.push(Move::new_quiet(from, to));
        }
    }
}

fn pseudo_legal_knight_moves(out: &mut MoveList, pos: &Position) {
    let t = tables();
    let mut bb = pos.pieces_of(pos.side_to_move, PieceType::Knight);
    while let Some(from) = bb.pop_lsb() {
        add_piece_moves_from_attacks(out, pos, from, t.knight[from.index()]);
    }
}

fn pseudo_legal_bishop_moves(out: &mut MoveList, pos: &Position) {
    let occ = pos.occupied();
    let mut bb = pos.pieces_of(pos.side_to_move, PieceType::Bishop);
    while let Some(from) = bb.pop_lsb() {
        add_piece_moves_from_attacks(out, pos, from, bishop_attacks(from, occ));
    }
}

fn pseudo_legal_rook_moves(out: &mut MoveList, pos: &Position) {
    let occ = pos.occupied();
    let mut bb = pos.pieces_of(pos.side_to_move, PieceType::Rook);
    while let Some(from) = bb.pop_lsb() {
        add_piece_moves_from_attacks(out, pos, from, rook_attacks(from, occ));
    }
}

fn pseudo_legal_queen_moves(out: &mut MoveList, pos: &Position) {
    let occ = pos.occupied();
    let mut bb = pos.pieces_of(pos.side_to_move, PieceType::Queen);
    while let Some(from) = bb.pop_lsb() {
        add_piece_moves_from_attacks(out, pos, from, queen_attacks(from, occ));
    }
}

fn pseudo_legal_king_moves(out: &mut MoveList, pos: &Position) {
    let stm = pos.side_to_move;
    let t = tables();
    let king_bb = pos.pieces_of(stm, PieceType::King);
    let from = match king_bb.lsb() {
        Some(sq) => sq,
        None => return,
    };
    add_piece_moves_from_attacks(out, pos, from, t.king[from.index()]);

    // Castling.
    let occ = pos.occupied();
    let opp = stm.opp();
    let (rank, ks_right, qs_right) = match stm {
        Color::White => (0u8, CastlingRights::WK, CastlingRights::WQ),
        Color::Black => (7u8, CastlingRights::BK, CastlingRights::BQ),
    };
    let king_home = Square::from_file_rank(4, rank);
    if from != king_home {
        return;
    }

    // King-side: f & g empty; e/f/g not attacked.
    if pos.castling.has(ks_right) {
        let f_sq = Square::from_file_rank(5, rank);
        let g_sq = Square::from_file_rank(6, rank);
        let path_clear = !occ.contains(f_sq) && !occ.contains(g_sq);
        if path_clear
            && !is_square_attacked(pos, king_home, opp)
            && !is_square_attacked(pos, f_sq, opp)
            && !is_square_attacked(pos, g_sq, opp)
        {
            out.push(Move::new_castle(king_home, g_sq));
        }
    }
    // Queen-side: b, c, d empty; e/d/c not attacked.
    if pos.castling.has(qs_right) {
        let b_sq = Square::from_file_rank(1, rank);
        let c_sq = Square::from_file_rank(2, rank);
        let d_sq = Square::from_file_rank(3, rank);
        let path_clear = !occ.contains(b_sq) && !occ.contains(c_sq) && !occ.contains(d_sq);
        if path_clear
            && !is_square_attacked(pos, king_home, opp)
            && !is_square_attacked(pos, d_sq, opp)
            && !is_square_attacked(pos, c_sq, opp)
        {
            out.push(Move::new_castle(king_home, c_sq));
        }
    }
}

fn pseudo_legal_moves(pos: &Position) -> MoveList {
    let mut out: MoveList = SmallVec::new();
    pseudo_legal_pawn_moves(&mut out, pos);
    pseudo_legal_knight_moves(&mut out, pos);
    pseudo_legal_bishop_moves(&mut out, pos);
    pseudo_legal_rook_moves(&mut out, pos);
    pseudo_legal_queen_moves(&mut out, pos);
    pseudo_legal_king_moves(&mut out, pos);
    out
}

// ---------------------------------------------------------------------------
// Legal-move filter
// ---------------------------------------------------------------------------

/// All legal moves available to the side-to-move on `pos`.
///
/// The returned list is in pseudo-legal generation order (pawns → knight →
/// bishop → rook → queen → king); callers that need a deterministic
/// canonical order should sort by `Move::raw()`.
pub fn legal_moves(pos: &Position) -> MoveList {
    let pseudo = pseudo_legal_moves(pos);
    let mut out: MoveList = SmallVec::with_capacity(pseudo.len());
    let stm = pos.side_to_move;
    for mv in pseudo.into_iter() {
        // Castling king-pass-through checks were already done in the
        // pseudo-legal generator. For all other moves we just need to check
        // that the moving side's king isn't in check after the move.
        let (after, _) = pos.make_move(mv);
        let king_sq = after.king_square(stm);
        if !is_square_attacked(&after, king_sq, stm.opp()) {
            out.push(mv);
        }
    }
    out
}

// ---------------------------------------------------------------------------
// Tests (local — perft tests live in `tests/perft.rs`)
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::position::Position;

    #[test]
    fn startpos_has_20_legal_moves() {
        let p = Position::startpos();
        assert_eq!(legal_moves(&p).len(), 20);
        assert!(!is_in_check(&p));
    }

    #[test]
    fn knight_attacks_h1_in_minimal_legal_position() {
        // White king on a1, white knight on h1, black king on a8 (kept far
        // from any white piece so the FEN is a legal position).
        let p = Position::from_fen("k7/8/8/8/8/8/8/K6N w - - 0 1").expect("valid FEN");
        let moves = legal_moves(&p);
        // White moves: knight (f2, g3), king (a2, b1, b2). Total = 5.
        assert_eq!(moves.len(), 5);
    }

    #[test]
    fn fools_mate_position_recognizes_check() {
        // After 1. f3 e5 2. g4 Qh4# — black to move position has black queen
        // on h4 attacking white king on e1. Use a simpler check: white king on
        // e1 in check from a black bishop on h4.
        let p = Position::from_fen("4k3/8/8/8/7b/8/8/4K3 w - - 0 1").unwrap();
        // bishop h4 -> e1 diagonal? h4=h-file rank 4, e1=e-file rank 1.
        // diagonal? h4-e1 difference: file -3, rank -3 -> yes, diagonal.
        assert!(is_in_check(&p), "white king should be in check");
    }
}
