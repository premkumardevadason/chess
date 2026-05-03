//! T094 — NNUE evaluation throughput benchmark.
//!
//! Target: ≥ 5 M evals/sec/thread per research.md R-10. Run with:
//!
//! ```text
//! cargo bench -p chess-engine --bench nnue
//! ```
//!
//! Each iteration runs a batch of 1 000 NNUE evaluations against a
//! pre-built `NNUEState` snapshot of the start position. Criterion's
//! `Throughput::Elements(1_000)` therefore reports the eval rate
//! directly.

use chess::{board::Board, nnue::NNUEState, piece::Color};
use criterion::{black_box, criterion_group, criterion_main, Criterion, Throughput};

fn bench_nnue_eval(c: &mut Criterion) {
    let board = Board::default();
    let state = NNUEState::from_board(&board);

    let mut group = c.benchmark_group("nnue_eval");
    group.throughput(Throughput::Elements(1_000));
    group.bench_function("startpos_x1000", |b| {
        b.iter(|| {
            let mut acc = 0i64;
            for _ in 0..1_000 {
                acc += black_box(state.evaluate(Color::White)) as i64;
            }
            black_box(acc)
        })
    });
    group.finish();
}

criterion_group!(benches, bench_nnue_eval);
criterion_main!(benches);
