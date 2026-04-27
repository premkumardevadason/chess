//! Integration smoke test for CP-C T019 / T020 / T021.
//!
//! Verifies — end to end — that the channel-based engine API works for a
//! single search, that the returned move is legal in `chess-core`, and
//! that `Command::Stop` aborts an infinite search within ≤ 200 ms (the
//! contract-stated bound is 50 ms; we allow 4× headroom for CI noise).

use std::time::{Duration, Instant};

use chess_core::{legal_moves, Position, STARTPOS_FEN};
use chess_engine::{Command, Engine, EngineConfig, Event, TimeControl};

fn drain_until<F: Fn(&Event) -> bool>(
    handle: &chess_engine::EngineHandle,
    f: F,
    timeout: Duration,
) -> Option<Event> {
    let started = Instant::now();
    while started.elapsed() < timeout {
        if let Some(ev) = handle.try_recv() {
            if f(&ev) {
                return Some(ev);
            }
        } else {
            std::thread::sleep(Duration::from_millis(5));
        }
    }
    None
}

#[test]
fn fixed_depth_search_returns_legal_move() {
    let engine = Engine::new();
    let handle = engine.spawn();

    let pos = Position::from_fen(STARTPOS_FEN).unwrap();
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .expect("set position");

    let cfg = EngineConfig {
        time_control: TimeControl::FixedDepth(4),
        max_threads: 1,
        ..EngineConfig::default()
    };
    handle
        .send(Command::StartSearch { config: cfg })
        .expect("start search");

    let ev = drain_until(
        &handle,
        |e| matches!(e, Event::SearchComplete(_) | Event::SearchAborted),
        Duration::from_secs(30),
    )
    .expect("did not receive SearchComplete in 30s");

    let result = match ev {
        Event::SearchComplete(r) => r,
        other => panic!("expected SearchComplete, got {:?}", other),
    };

    let legal: Vec<_> = legal_moves(&pos).into_iter().collect();
    assert!(
        legal.contains(&result.mv),
        "engine returned illegal move {} at startpos",
        result.mv.to_long_algebraic()
    );
}

#[test]
fn stop_command_aborts_infinite_search_within_200ms() {
    let engine = Engine::new();
    let handle = engine.spawn();

    let pos = Position::from_fen(STARTPOS_FEN).unwrap();
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .expect("set position");

    let cfg = EngineConfig {
        time_control: TimeControl::Infinite,
        max_threads: 1,
        ..EngineConfig::default()
    };
    handle
        .send(Command::StartSearch { config: cfg })
        .expect("start search");

    // Let the search ramp up.
    std::thread::sleep(Duration::from_millis(150));

    let stop_sent_at = Instant::now();
    handle.send(Command::Stop).expect("send stop");

    let ev = drain_until(
        &handle,
        |e| matches!(e, Event::SearchAborted | Event::SearchComplete(_)),
        Duration::from_secs(2),
    )
    .expect("no abort event within 2s");
    let abort_latency = stop_sent_at.elapsed();

    assert!(
        matches!(ev, Event::SearchAborted | Event::SearchComplete(_)),
        "unexpected event: {:?}",
        ev
    );
    assert!(
        abort_latency <= Duration::from_millis(200),
        "Stop -> SearchAborted latency {abort_latency:?} > 200 ms (contract: 50 ms; CI tolerance: 200 ms)"
    );
}
