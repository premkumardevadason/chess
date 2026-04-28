//! T069 — SAN move-list snapshot test.
//!
//! Plays a curated game that exercises several special-move kinds
//! (castling on both sides, en-passant capture) and snapshots the
//! resulting move-list SAN output via the `insta` crate. Future
//! regressions in SAN rendering or move-history bookkeeping will fail
//! this test until the snapshot is consciously updated with
//! `cargo insta accept`.
//!
//! Per [research.md R-11](../../specs/001-chess-ai-rewrite/research.md#r-11-test-framework)
//! `insta` is the workspace's chosen snapshot-testing harness.
//!
//! ## Curated game
//!
//! 30-ply position from the Pirc-style line:
//! 1. e4 a6
//! 2. e5 d5
//! 3. exd6 cxd6     <-- en-passant capture (White)
//! 4. Nf3 Nf6
//! 5. Nc3 g6
//! 6. d4 Bg7
//! 7. Bc4 O-O       <-- Black king-side castle
//! 8. O-O Nc6       <-- White king-side castle
//! 9. Re1 Bg4
//! 10. h3 Bxf3
//! 11. Qxf3 e5
//! 12. d5 Nd4
//! 13. Qd1 b5
//! 14. Bb3 a5
//! 15. a4 b4

use chess_core::{Color, Game, GameMode, san::parse_san};

/// Plays a SAN sequence, returning the joined SAN strings produced by
/// `MoveRecord::san` (the canonical rendering, not the input string).
fn play_and_render_sans(moves: &[&str]) -> String {
    let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
    for san in moves {
        let mv = parse_san(&g.current, san)
            .unwrap_or_else(|e| panic!("parse '{san}' failed: {e:?}"));
        g.make_move(mv)
            .unwrap_or_else(|e| panic!("play '{san}' failed: {e:?}"));
    }

    // Render in algebraic-notation move-pair form: "1. e4 a6\n2. e5 d5\n…".
    let plies: Vec<&str> = g.history.iter().map(|r| r.san.as_str()).collect();
    let mut out = String::new();
    let mut i = 0;
    while i < plies.len() {
        let move_no = (i / 2) + 1;
        let white = plies[i];
        let black = plies.get(i + 1).copied().unwrap_or("");
        if black.is_empty() {
            out.push_str(&format!("{move_no}. {white}\n"));
        } else {
            out.push_str(&format!("{move_no}. {white} {black}\n"));
        }
        i += 2;
    }
    out
}

#[test]
fn move_list_snapshot_curated_game() {
    let moves = [
        "e4", "a6",
        "e5", "d5",
        "exd6", "cxd6",
        "Nf3", "Nf6",
        "Nc3", "g6",
        "d4", "Bg7",
        "Bc4", "O-O",
        "O-O", "Nc6",
        "Re1", "Bg4",
        "h3", "Bxf3",
        "Qxf3", "e5",
        "d5", "Nd4",
        "Qd1", "b5",
        "Bb3", "a5",
        "a4", "b4",
    ];
    let rendered = play_and_render_sans(&moves);
    insta::assert_snapshot!("curated_game_30ply", rendered);
}
