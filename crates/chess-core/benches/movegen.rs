//! T093 — Movegen benchmark (perft 6 from initial position).
//!
//! Target: ≥ 100 M nps per research.md R-10. Run with:
//!
//! ```text
//! cargo bench -p chess-core --bench movegen
//! ```
//!
//! The bench reports throughput in nodes/second using the well-known
//! perft(6) leaf count of 119 060 324 from the standard initial
//! position. Criterion's `Throughput::Elements(N)` makes the headline
//! number directly comparable to engines that quote nps.

use chess_core::{legal_moves, Position, STARTPOS_FEN};
use criterion::{criterion_group, criterion_main, BenchmarkId, Criterion, Throughput};

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

fn bench_perft(c: &mut Criterion) {
    let pos = Position::from_fen(STARTPOS_FEN).unwrap();

    // perft(5) = 4 865 609 leaves — fast feedback during tuning.
    {
        let mut group = c.benchmark_group("perft_startpos");
        group.sample_size(10);
        group.throughput(Throughput::Elements(4_865_609));
        group.bench_function(BenchmarkId::from_parameter(5), |b| {
            b.iter(|| perft(&pos, 5))
        });
        group.finish();
    }

    // perft(6) = 119 060 324 leaves — the "nps" headline number.
    {
        let mut group = c.benchmark_group("perft_startpos");
        group.sample_size(10);
        group.throughput(Throughput::Elements(119_060_324));
        group.bench_function(BenchmarkId::from_parameter(6), |b| {
            b.iter(|| perft(&pos, 6))
        });
        group.finish();
    }
}

criterion_group!(benches, bench_perft);
criterion_main!(benches);
