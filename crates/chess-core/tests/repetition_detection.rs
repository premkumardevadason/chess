//! T070 — automatic-draw detection tests.
//!
//! Verifies [`Game`] auto-sets `result = Some(Draw(...))` exactly on
//! the move that triggers:
//!
//! 1. **Threefold repetition** — knight-shuffle from the starting
//!    position returns to the start three times.
//! 2. **50-move rule** — the position's `halfmove_clock` reaches 100
//!    plies without a pawn push or capture.
//!
//! Both rules are described in [spec.md Edge Cases](../../specs/001-chess-ai-rewrite/spec.md#edge-cases)
//! (FR-006). The repetition path through `Game::make_move` is the
//! one used in production; the 50-move path is exercised by directly
//! seeding the half-move counter (the alternative — playing 99 legal
//! reversible moves — would also trigger threefold first, masking
//! the rule under test).

use chess_core::{
    Color, DrawReason, Game, GameMode, GameResult, san::parse_san,
};

#[test]
fn threefold_repetition_triggers_on_third_occurrence() {
    let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));

    // Knight-shuffle: each 4-ply cycle (Nf3 Nf6 Ng1 Ng8) restores the
    // starting position. The starting position counts as occurrence
    // #1; after 4 plies we have #2; after 8 plies we have #3.
    let cycle = ["Nf3", "Nf6", "Ng1", "Ng8"];

    // First cycle (plies 1–4): position returns to startpos for the
    // 2nd time — should NOT yet be drawn.
    for san in cycle {
        let mv = parse_san(&g.current, san).expect(san);
        g.make_move(mv).expect(san);
    }
    assert!(
        g.result().is_none(),
        "second occurrence should not yet be a draw, got {:?}",
        g.result(),
    );

    // First three plies of the second cycle keep the game live.
    for san in &cycle[..3] {
        let mv = parse_san(&g.current, san).expect(san);
        g.make_move(mv).expect(san);
        assert!(
            g.result().is_none(),
            "draw should not fire before the 3rd occurrence (after {san})",
        );
    }

    // The 8th ply (Ng8) returns the position to startpos for the
    // third time — threefold MUST fire on exactly this move.
    let last = parse_san(&g.current, cycle[3]).expect(cycle[3]);
    g.make_move(last).expect(cycle[3]);
    assert_eq!(
        g.result(),
        Some(GameResult::Draw(DrawReason::ThreefoldRepetition)),
        "threefold repetition should fire on the third visit to startpos",
    );
}

#[test]
fn fifty_move_rule_triggers_when_halfmove_clock_reaches_100() {
    let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));

    // Get the position out of the starting bookkeeping (so the next
    // reversible move doesn't also trip threefold) and seed the
    // halfmove clock to 99. The next non-pawn, non-capture move
    // bumps it to 100 → 50-move rule fires.
    let nf3 = parse_san(&g.current, "Nf3").unwrap();
    g.make_move(nf3).unwrap();
    let nf6 = parse_san(&g.current, "Nf6").unwrap();
    g.make_move(nf6).unwrap();
    assert!(g.result().is_none());

    // Hand-seed the halfmove counter; this mirrors arriving at the
    // position via 99 reversible plies but avoids the threefold trap
    // that any natural shuffle would also trigger.
    g.current.halfmove_clock = 99;

    // Any legal non-pawn, non-capture move now ticks the clock to 100.
    let ng1 = parse_san(&g.current, "Ng1").unwrap();
    g.make_move(ng1).unwrap();

    assert_eq!(
        g.result(),
        Some(GameResult::Draw(DrawReason::FiftyMoveRule)),
        "50-move rule should fire when halfmove_clock reaches 100",
    );
    assert_eq!(g.current.halfmove_clock, 100);
}
