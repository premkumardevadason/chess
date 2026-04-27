//! UI smoke test (T052).
//!
//! Drives [`chess_app::ui::game_screen::GameScreen`] without an egui
//! event loop. Verifies the click-click move pipeline:
//!
//! 1. Create a fresh `GameScreen` (Human plays White) plus a real
//!    [`chess_app::engine_link::EngineLink`].
//! 2. Simulate clicking `e2`, then `e4` (analogous to the egui
//!    `BoardWidget::clicked` flow).
//! 3. Assert the board state advanced (history length 1, `e2-e4` played).
//! 4. Assert that on the next `tick_engine` call the engine is asked to
//!    start searching (per [contracts/ui-interactions.md §7]).
//!
//! The test exercises the same code path the GUI uses every frame,
//! minus the egui rendering, which is sufficient to catch regressions
//! in the click → move → engine-trigger pipeline. It does NOT spin up
//! `eframe::run_native` and therefore stays fast (~200 ms) and headless.
//!
//! [contracts/ui-interactions.md §7]:
//!     ../../specs/001-chess-ai-rewrite/contracts/ui-interactions.md

use std::time::Duration;

use chess_app::engine_link::EngineLink;
use chess_app::settings::UserSettings;
use chess_app::ui::board::{BoardResponse, ClickOutcome};
use chess_app::ui::GameScreen;
use chess_core::Square;

/// Build a `BoardResponse` for a single click on `sq`.
fn click_response(sq: Square) -> BoardResponse {
    BoardResponse {
        clicked: Some(sq),
        ..BoardResponse::default()
    }
}

#[test]
fn click_e2_then_e4_plays_the_move_and_kicks_engine() {
    // Use a defaulted `UserSettings` (Human plays White, default time
    // control). The engine real-spawns a worker thread; that's fine
    // because we never wait for a search to complete here.
    let mut screen = GameScreen::new(UserSettings::default());
    let mut engine = EngineLink::spawn();

    // Sanity: it's White's turn, no history yet.
    assert!(screen.game.history.is_empty(), "fresh game has no history");
    assert!(screen.is_human_turn_for_test());

    let e2 = Square::from_algebraic("e2").expect("e2");
    let e4 = Square::from_algebraic("e4").expect("e4");

    // First click: select e2.
    screen.handle_board_response_for_test(click_response(e2));
    assert_eq!(
        screen.board_state.selected,
        Some(e2),
        "first click should select e2"
    );
    assert!(
        screen.board_state.legal_targets.contains(&e4),
        "selecting e2 should expose e4 as a legal target"
    );

    // Second click: complete e2 -> e4.
    screen.handle_board_response_for_test(click_response(e4));

    assert_eq!(
        screen.game.history.len(),
        1,
        "click-click should produce exactly one move"
    );
    let played = screen.game.history[0].move_played;
    assert_eq!(played.from(), e2, "first ply should originate at e2");
    assert_eq!(played.to(), e4, "first ply should land at e4");

    // After the human move it's the engine's turn — `tick_engine` must
    // queue a `StartSearch`.
    screen.tick_engine_for_test(&mut engine);
    assert!(
        screen.engine_searching,
        "tick_engine must trigger an engine search after the human move"
    );

    // Be a good citizen: drain a few events so the engine isn't left
    // mid-search by the time the test process exits.
    engine.stop();
    engine.shutdown();
    // Give the worker a beat to react before we drop the link.
    std::thread::sleep(Duration::from_millis(20));
    let _ = engine.tick();
}

#[test]
fn click_outcome_dispatches_play_move() {
    // Independent unit-style test: the click dispatch helper handles
    // `ClickOutcome::PlayMove` by invoking `try_play_human_move`.
    let mut screen = GameScreen::new(UserSettings::default());
    let pos = screen.game.current;
    let legal = chess_core::legal_moves(&pos);
    let e2e4 = legal
        .into_iter()
        .find(|m| {
            m.from() == Square::from_algebraic("e2").unwrap()
                && m.to() == Square::from_algebraic("e4").unwrap()
        })
        .expect("e2-e4 is legal at startpos");

    screen.apply_click_outcome_for_test(ClickOutcome::PlayMove(e2e4));
    assert_eq!(screen.game.history.len(), 1);
    assert_eq!(screen.game.history[0].move_played, e2e4);
}
