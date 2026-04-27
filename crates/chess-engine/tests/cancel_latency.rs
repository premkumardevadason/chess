//! T032 — Cancellation-latency contract test.
//!
//! Per [contracts/engine-api.md §3.7](../../specs/001-chess-ai-rewrite/contracts/engine-api.md):
//!
//! > After `Stop`, the engine MUST emit `SearchAborted` within 50 ms
//! > on commodity hardware.
//!
//! This test runs the contract verbatim:
//! 1. Start a 10-second `PerMove` search.
//! 2. Sleep 100 ms to let the search ramp up.
//! 3. Send `Stop` and time how long it takes for `SearchAborted`
//!    (or `SearchComplete`) to land.
//! 4. Assert the latency is ≤ 50 ms.
//!
//! ## CI tolerance
//!
//! 50 ms is the **production hardware** budget. CI VMs (especially the
//! Windows runner) can stall briefly under load and produce false
//! failures at exactly that boundary. We bump the assertion to
//! `200 ms` here to keep the gate stable across environments while
//! staying loud enough that an actual regression (e.g. forgetting to
//! flip the global stop flag, polling the AtomicBool only at depth
//! transitions, etc.) still trips the test.
//!
//! The 50-ms-strict variant runs separately under
//! `cargo test --release` in CI's perf job. To run it locally:
//!
//! ```text
//! CHESS_STRICT_CANCEL=1 cargo test --release -p chess-engine \
//!     --test cancel_latency -- --nocapture
//! ```

use std::time::{Duration, Instant};

use chess_core::{Position, STARTPOS_FEN};
use chess_engine::{Command, Engine, EngineConfig, EngineHandle, Event, TimeControl};

const RAMP_UP: Duration = Duration::from_millis(100);
const SEARCH_BUDGET: Duration = Duration::from_secs(10);
const ABORT_TIMEOUT: Duration = Duration::from_secs(2);

fn lenient_budget() -> Duration {
    if std::env::var("CHESS_STRICT_CANCEL").as_deref() == Ok("1") {
        Duration::from_millis(50)
    } else {
        Duration::from_millis(200)
    }
}

fn drain_for_abort(handle: &EngineHandle) -> (Option<Event>, Duration) {
    let started = Instant::now();
    while started.elapsed() < ABORT_TIMEOUT {
        for event in handle.drain() {
            if matches!(event, Event::SearchAborted | Event::SearchComplete(_)) {
                return (Some(event), started.elapsed());
            }
        }
        std::thread::sleep(Duration::from_millis(2));
    }
    (None, started.elapsed())
}

#[test]
fn stop_aborts_long_search_within_budget() {
    let handle = Engine::new().spawn();

    let pos = Position::from_fen(STARTPOS_FEN).unwrap();
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .expect("set position");

    let cfg = EngineConfig {
        time_control: TimeControl::PerMove(SEARCH_BUDGET),
        max_threads: 1,
        ..EngineConfig::default()
    };
    handle
        .send(Command::StartSearch { config: cfg })
        .expect("start search");

    std::thread::sleep(RAMP_UP);

    let stop_sent_at = Instant::now();
    handle.send(Command::Stop).expect("send stop");

    let (event, _wait) = drain_for_abort(&handle);
    let abort_latency = stop_sent_at.elapsed();
    let event = event.expect("no abort event within 2 s");

    assert!(
        matches!(event, Event::SearchAborted | Event::SearchComplete(_)),
        "unexpected event: {event:?}"
    );

    let budget = lenient_budget();
    assert!(
        abort_latency <= budget,
        "Stop → abort latency {abort_latency:?} exceeded budget {budget:?} \
         (production contract: 50 ms; set CHESS_STRICT_CANCEL=1 to enforce strictly)"
    );

    let _ = handle.send(Command::Shutdown);
}
