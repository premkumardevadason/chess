//! T095 — Search benchmark (depth 12 from Kiwipete on 8 threads).
//!
//! Target: ≥ 1.5 M nps per research.md R-10. Run with:
//!
//! ```text
//! cargo bench -p chess-engine --bench search
//! ```
//!
//! Each iteration runs a fixed-depth(12) search to completion and
//! reports the engine-reported node count (`SearchInfo.nodes`) as the
//! throughput element so the criterion summary line is in nps.

use std::time::{Duration, Instant};

use chess_core::Position;
use chess_engine::{Command, Engine, EngineConfig, Event, TimeControl};
use criterion::{black_box, criterion_group, criterion_main, Criterion, Throughput};

/// Kiwipete (Position 2 from chessprogramming.org).
const KIWIPETE_FEN: &str =
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

fn run_one_search(engine: &chess_engine::EngineHandle, pos: &Position) -> u64 {
    engine
        .send(Command::SetPosition {
            position: *pos,
            history: vec![],
        })
        .unwrap();
    engine
        .send(Command::StartSearch {
            config: EngineConfig {
                time_control: TimeControl::FixedDepth(12),
                max_threads: 8,
                ..EngineConfig::default()
            },
        })
        .unwrap();

    let deadline = Instant::now() + Duration::from_secs(120);
    while Instant::now() < deadline {
        if let Some(ev) = engine.try_recv() {
            if let Event::SearchComplete(r) = ev {
                return r.info.nodes;
            }
        } else {
            std::thread::sleep(Duration::from_millis(2));
        }
    }
    panic!("search did not complete within 120s");
}

fn bench_search(c: &mut Criterion) {
    let pos = Position::from_fen(KIWIPETE_FEN).unwrap();
    let engine = Engine::new();
    let handle = engine.spawn();

    // Warm the transposition table / NNUE caches once before measuring.
    let warmup_nodes = run_one_search(&handle, &pos);

    let mut group = c.benchmark_group("search_kiwipete_d12_t8");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(60));
    group.throughput(Throughput::Elements(warmup_nodes.max(1)));
    group.bench_function("fixed_depth_12", |b| {
        b.iter(|| black_box(run_one_search(&handle, &pos)))
    });
    group.finish();
}

criterion_group!(benches, bench_search);
criterion_main!(benches);
