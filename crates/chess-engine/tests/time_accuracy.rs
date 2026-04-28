//! T063 — Time-accuracy validation.
//!
//! At each per-move setting in `[100ms, 500ms, 1s, 5s, 10s]` plays N
//! moves in random midgame positions and asserts:
//!
//!  * ≥ 99% of searches finish within ±20% of the budget; AND
//!  * **none** exceed the budget by more than 50%.
//!
//! Per [SC-004](../../specs/001-chess-ai-rewrite/spec.md#sc-004).
//!
//! The full 50-move-per-budget sweep takes several minutes and is
//! `#[ignore]`d. Invoke with:
//!
//! ```text
//! cargo test -p chess-engine --test time_accuracy \
//!     --release -- --ignored --nocapture
//! ```

use std::time::{Duration, Instant};

use chess_core::Position;
use chess_engine::{
    Command, Engine, EngineConfig, EngineHandle, Event, Mode, TimeControl,
};

/// A handful of mid-game positions taken from public domain games.
/// Selecting a small fixed set keeps the harness deterministic; the
/// engine seed is varied per move to keep search trees distinct.
const MIDGAME_FENS: &[&str] = &[
    "r1bq1rk1/pp2bppp/2n2n2/2pp4/3P4/2NBPN2/PP3PPP/R1BQ1RK1 w - - 0 9",
    "r2qk2r/pp1nbppp/2p1pn2/3p4/2PP4/P1NBPN2/1PQ2PPP/R1B1K2R w KQkq - 0 9",
    "rnbq1rk1/pp3ppp/4pn2/2pp4/3P4/2NBPN2/PPP2PPP/R1BQ1RK1 w - - 0 7",
    "r1bqr1k1/pp2ppbp/2np1np1/8/2P5/2N1PN1P/PP1BBPP1/R2Q1RK1 w - - 0 10",
    "r1b2rk1/pp1nqppp/2p1pn2/3p4/2PP4/P1NBPN2/1PQ2PPP/R1B2RK1 w - - 0 10",
    "r3k2r/pppq1ppp/2n1bn2/3pp3/3PP3/2NBBN2/PPPQ1PPP/R3K2R w KQkq - 0 8",
    "rnbq1rk1/ppp1bppp/3p1n2/3Pp3/2P5/2N2NP1/PP2PPBP/R1BQ1RK1 w - - 0 7",
    "r2q1rk1/pp1bppbp/2np1np1/8/2P5/2N1PN1P/PPQ1BPP1/R1B2RK1 w - - 0 10",
];

/// Number of measurements per budget.
const SAMPLES_PER_BUDGET: usize = 50;

/// Budgets we sweep through.
const BUDGETS: &[Duration] = &[
    Duration::from_millis(100),
    Duration::from_millis(500),
    Duration::from_secs(1),
    Duration::from_secs(5),
    Duration::from_secs(10),
];

fn measure_one(handle: &EngineHandle, pos: Position, budget: Duration, seed: u64) -> Duration {
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .expect("send SetPosition");
    handle
        .send(Command::StartSearch {
            config: EngineConfig {
                mode: Mode::Reproducible { seed },
                time_control: TimeControl::PerMove(budget),
                max_threads: 1,
                ..EngineConfig::default()
            },
        })
        .expect("send StartSearch");

    let start = Instant::now();
    // Allow up to 3× budget before declaring the engine non-responsive.
    let hard_cap = budget * 3 + Duration::from_secs(2);
    while start.elapsed() < hard_cap {
        for ev in handle.drain() {
            match ev {
                Event::SearchComplete(_) | Event::SearchAborted => return start.elapsed(),
                _ => {}
            }
        }
        std::thread::sleep(Duration::from_millis(2));
    }
    panic!("engine did not return within {hard_cap:?} for budget {budget:?}");
}

#[test]
#[ignore = "long-running time-accuracy validation; run with --ignored"]
fn per_move_budget_is_respected_within_tolerance() {
    let handle = Engine::new().spawn();

    let mut total = 0usize;
    let mut within_tolerance = 0usize;
    let mut overruns_50pct = 0usize;
    let mut worst_overrun_pct: f64 = 0.0;

    for &budget in BUDGETS {
        let lower = budget.as_secs_f64() * 0.80;
        let upper = budget.as_secs_f64() * 1.20;
        let hard = budget.as_secs_f64() * 1.50;
        for s in 0..SAMPLES_PER_BUDGET {
            let fen = MIDGAME_FENS[s % MIDGAME_FENS.len()];
            let pos = Position::from_fen(fen).expect("parse");
            let seed = 0xC0FFEE_u64
                .wrapping_add(budget.as_millis() as u64)
                .wrapping_mul(0x9E3779B1)
                .wrapping_add(s as u64);
            let elapsed = measure_one(&handle, pos, budget, seed);
            let secs = elapsed.as_secs_f64();
            total += 1;
            if secs >= lower && secs <= upper {
                within_tolerance += 1;
            }
            if secs > hard {
                overruns_50pct += 1;
                let pct = (secs / budget.as_secs_f64() - 1.0) * 100.0;
                if pct > worst_overrun_pct {
                    worst_overrun_pct = pct;
                }
            }
            if secs > budget.as_secs_f64() * 1.20 {
                let pct = (secs / budget.as_secs_f64() - 1.0) * 100.0;
                if pct > worst_overrun_pct {
                    worst_overrun_pct = pct;
                }
            }
            eprintln!(
                "[time_acc] budget={:?} sample={:>2}/{} elapsed={:?} ({:>+5.1}%)",
                budget,
                s + 1,
                SAMPLES_PER_BUDGET,
                elapsed,
                (secs / budget.as_secs_f64() - 1.0) * 100.0
            );
        }
    }
    let _ = handle.send(Command::Shutdown);

    eprintln!(
        "[time_acc] total={} within_±20%={} overruns_>50%={} worst_overrun={:.1}%",
        total, within_tolerance, overruns_50pct, worst_overrun_pct
    );

    let in_band_ratio = within_tolerance as f64 / total as f64;
    assert!(
        in_band_ratio >= 0.99,
        "only {:.1}% of searches were within ±20% of budget (need ≥ 99%)",
        in_band_ratio * 100.0
    );
    assert_eq!(
        overruns_50pct, 0,
        "{} searches exceeded budget by more than 50% (worst {:.1}%)",
        overruns_50pct, worst_overrun_pct
    );
}
