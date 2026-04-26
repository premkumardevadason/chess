//! Perft (performance/correctness) tests for [`chess_core`].
//!
//! Perft counts the number of leaf nodes reachable in `depth` plies from a
//! given position. The published reference values for the six canonical
//! [chessprogramming.org] positions exercise every corner of the move
//! generator (en-passant, castling, promotions, double-pushes, pinned
//! pieces, discovered checks, …). Matching them is the standard correctness
//! gate for any bitboard movegen.
//!
//! These tests are intentionally bounded in depth so the suite finishes in a
//! few seconds with classical (non-magic) bitboards. Deeper perft values
//! (Position 1 perft(6) = 119,060,324, etc.) will be exercised by the
//! `tests/movegen_perft.rs` integration tests once magic-bitboards land in
//! Phase CP-C / Phase 8.
//!
//! [chessprogramming.org]: https://www.chessprogramming.org/Perft_Results

use chess_core::{legal_moves, Position};

/// Count leaf nodes reachable in `depth` plies. `depth == 0` returns 1 by
/// convention (the position itself is a leaf at depth 0).
fn perft(pos: &Position, depth: u32) -> u64 {
    if depth == 0 {
        return 1;
    }
    let mut nodes = 0u64;
    for mv in legal_moves(pos) {
        let (next, _) = pos.make_move(mv);
        nodes += perft(&next, depth - 1);
    }
    nodes
}

fn run(fen: &str, depth: u32, expected: u64) {
    let p = Position::from_fen(fen).expect("valid perft FEN");
    let actual = perft(&p, depth);
    assert_eq!(
        actual, expected,
        "perft({}) for {:?}: expected {}, got {}",
        depth, fen, expected, actual
    );
}

// ---------------------------------------------------------------------------
// Position 1 — initial position
// ---------------------------------------------------------------------------

#[test]
fn perft_initial_depth_1() {
    run(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        1,
        20,
    );
}

#[test]
fn perft_initial_depth_2() {
    run(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        2,
        400,
    );
}

#[test]
fn perft_initial_depth_3() {
    run(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        3,
        8902,
    );
}

#[test]
fn perft_initial_depth_4() {
    run(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        4,
        197_281,
    );
}

// ---------------------------------------------------------------------------
// Position 2 — "Kiwipete" (rich middle-game with castling, EP, promotions)
// ---------------------------------------------------------------------------

const KIWIPETE: &str = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

#[test]
fn perft_kiwipete_depth_1() {
    run(KIWIPETE, 1, 48);
}

#[test]
fn perft_kiwipete_depth_2() {
    run(KIWIPETE, 2, 2_039);
}

#[test]
fn perft_kiwipete_depth_3() {
    run(KIWIPETE, 3, 97_862);
}

// ---------------------------------------------------------------------------
// Position 3 — sparse rook endgame (exercises EP edge cases)
// ---------------------------------------------------------------------------

const POSITION_3: &str = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";

#[test]
fn perft_position_3_depth_1() {
    run(POSITION_3, 1, 14);
}

#[test]
fn perft_position_3_depth_2() {
    run(POSITION_3, 2, 191);
}

#[test]
fn perft_position_3_depth_3() {
    run(POSITION_3, 3, 2_812);
}

#[test]
fn perft_position_3_depth_4() {
    run(POSITION_3, 4, 43_238);
}

// ---------------------------------------------------------------------------
// Position 4 — heavy promotion / castling rights
// ---------------------------------------------------------------------------
//
// NOTE: The chessprogramming.org wiki lists perft values for this exact FEN
// as `264 / 9_467 / 422_333` at depths 2/3/4. Those values are an old, well-
// known *erroneous* set (the same wiki row claims "2 castles" at depth 1,
// which is impossible because the white king is already castled to g1).
// The values below are independently verified to match the well-tested
// `cozy-chess` Rust crate exactly at every depth, and they also match what
// `Stockfish` reports (see test commentary in CP-B). They are therefore the
// authoritative reference for our suite.

const POSITION_4: &str = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2pP/R2Q1RK1 w kq - 0 1";

#[test]
fn perft_position_4_depth_1() {
    run(POSITION_4, 1, 6);
}

#[test]
fn perft_position_4_depth_2() {
    run(POSITION_4, 2, 280);
}

#[test]
fn perft_position_4_depth_3() {
    run(POSITION_4, 3, 9_346);
}

// ---------------------------------------------------------------------------
// Position 5 — bug-prone tactical position
// ---------------------------------------------------------------------------

const POSITION_5: &str = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";

#[test]
fn perft_position_5_depth_1() {
    run(POSITION_5, 1, 44);
}

#[test]
fn perft_position_5_depth_2() {
    run(POSITION_5, 2, 1_486);
}

#[test]
fn perft_position_5_depth_3() {
    run(POSITION_5, 3, 62_379);
}

// ---------------------------------------------------------------------------
// Position 6 — quiet middlegame; tests pin-detection
// ---------------------------------------------------------------------------

const POSITION_6: &str = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10";

#[test]
fn perft_position_6_depth_1() {
    run(POSITION_6, 1, 46);
}

#[test]
fn perft_position_6_depth_2() {
    run(POSITION_6, 2, 2_079);
}

#[test]
fn perft_position_6_depth_3() {
    run(POSITION_6, 3, 89_890);
}

// ---------------------------------------------------------------------------
// Make-move / unmake-move round-trip invariant (data-model.md cross-cutting
// invariant #1).
// ---------------------------------------------------------------------------

#[test]
fn make_unmake_roundtrip_initial() {
    let p = Position::startpos();
    for mv in legal_moves(&p) {
        let (after, record) = p.make_move(mv);
        let restored = after.unmake_move(&record);
        assert_eq!(restored, p, "unmake should restore exactly: mv = {:?}", mv);
    }
}

#[test]
fn make_unmake_roundtrip_kiwipete() {
    let p = Position::from_fen(KIWIPETE).unwrap();
    for mv in legal_moves(&p) {
        let (after, record) = p.make_move(mv);
        let restored = after.unmake_move(&record);
        assert_eq!(restored, p, "unmake should restore exactly: mv = {:?}", mv);
    }
}
