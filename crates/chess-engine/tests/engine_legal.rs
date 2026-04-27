//! T031 — Engine-legality property test.
//!
//! For 50 deterministically-shuffled positions (RNG seed
//! `0xDEAD_BEEF`), spawn the engine, run a `FixedDepth(4)` search,
//! and assert the returned `BestMove.mv` is in
//! `chess_core::legal_moves(p)`.
//!
//! Additionally, for the first 20 positions, repeat the search with
//! `TimeControl::PerMove(1ms)` and assert a legal move is still
//! returned (closes the pathological-budget edge case F8 from
//! [spec.md → Edge Cases](../../specs/001-chess-ai-rewrite/spec.md)).
//!
//! The seed is hard-coded for reproducibility; bumping it requires a
//! deliberate code change reviewable in PR.

use std::time::{Duration, Instant};

use chess_core::{legal_moves, Position};
use chess_engine::{Command, Engine, EngineConfig, EngineHandle, Event, TimeControl};

/// Test seed — never change. If the seed needs to change, copy this
/// constant to a new name and document why.
const SEED: u64 = 0xDEAD_BEEFu64;

/// 6 canonical FIDE perft positions. We grow the corpus from these
/// using the seeded RNG, so the test corpus is reproducible without
/// shipping a separate EPD file.
const SEED_FENS: &[&str] = &[
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2pP/R2Q1RK1 w kq - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
];

/// Tiny xoshiro256** RNG so the test pulls no extra dependencies.
struct Xoshiro256 {
    s: [u64; 4],
}

impl Xoshiro256 {
    fn new(seed: u64) -> Self {
        // SplitMix64 to spread the user seed across the four words.
        let mut z = seed;
        let mut splitmix = || -> u64 {
            z = z.wrapping_add(0x9E37_79B9_7F4A_7C15);
            let mut x = z;
            x = (x ^ (x >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
            x = (x ^ (x >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
            x ^ (x >> 31)
        };
        Self {
            s: [splitmix(), splitmix(), splitmix(), splitmix()],
        }
    }

    fn next_u64(&mut self) -> u64 {
        let result = self.s[1].wrapping_mul(5).rotate_left(7).wrapping_mul(9);
        let t = self.s[1] << 17;
        self.s[2] ^= self.s[0];
        self.s[3] ^= self.s[1];
        self.s[1] ^= self.s[2];
        self.s[0] ^= self.s[3];
        self.s[2] ^= t;
        self.s[3] = self.s[3].rotate_left(45);
        result
    }

    fn next_usize_below(&mut self, n: usize) -> usize {
        (self.next_u64() % n as u64) as usize
    }
}

/// Build a 50-position corpus by random-walking each seed FEN through
/// a small number of legal moves. The RNG state determines the walk,
/// so the resulting corpus is bit-identical across runs / machines.
fn build_corpus() -> Vec<Position> {
    let mut rng = Xoshiro256::new(SEED);
    let mut out = Vec::with_capacity(50);
    for fen in SEED_FENS {
        out.push(Position::from_fen(fen).expect("valid seed FEN"));
    }
    while out.len() < 50 {
        let parent_idx = rng.next_usize_below(out.len());
        let parent = out[parent_idx];
        let walks = 1 + rng.next_usize_below(6);
        let mut current = parent;
        let mut walked_ok = true;
        for _ in 0..walks {
            let moves = legal_moves(&current);
            if moves.is_empty() {
                walked_ok = false;
                break;
            }
            let pick = rng.next_usize_below(moves.len());
            let mv = moves[pick];
            current = current.make_move(mv).0;
        }
        if walked_ok {
            out.push(current);
        }
    }
    out
}

fn run_search(handle: &EngineHandle, pos: Position, tc: TimeControl) -> Option<chess_core::Move> {
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .ok()?;
    handle
        .send(Command::StartSearch {
            config: EngineConfig {
                time_control: tc,
                max_threads: 1,
                ..EngineConfig::default()
            },
        })
        .ok()?;

    let started = Instant::now();
    let timeout = Duration::from_secs(30);
    while started.elapsed() < timeout {
        for event in handle.drain() {
            match event {
                Event::SearchComplete(result) => return Some(result.mv),
                Event::SearchAborted => return None,
                _ => {}
            }
        }
        std::thread::sleep(Duration::from_millis(5));
    }
    None
}

#[test]
fn engine_returns_legal_moves_at_depth_4() {
    let corpus = build_corpus();
    assert_eq!(corpus.len(), 50, "expected 50 corpus positions");

    let handle = Engine::new().spawn();
    for (i, pos) in corpus.iter().enumerate() {
        let legal: Vec<_> = legal_moves(pos).into_iter().collect();
        if legal.is_empty() {
            // Terminal position — engine has nothing legal to return,
            // skip it. The corpus walk should not produce many but a
            // few are tolerated.
            continue;
        }
        let mv = run_search(&handle, *pos, TimeControl::FixedDepth(4))
            .unwrap_or_else(|| panic!("no SearchComplete for corpus[{i}]"));
        assert!(
            legal.contains(&mv),
            "engine returned illegal move {} on corpus[{i}] FEN {:?}",
            mv.to_long_algebraic(),
            pos.to_fen()
        );
    }
    let _ = handle.send(Command::Shutdown);
}

#[test]
fn engine_returns_legal_move_under_one_millisecond_budget() {
    let corpus = build_corpus();
    let subset: Vec<_> = corpus.into_iter().take(20).collect();

    let handle = Engine::new().spawn();
    for (i, pos) in subset.iter().enumerate() {
        let legal: Vec<_> = legal_moves(pos).into_iter().collect();
        if legal.is_empty() {
            continue;
        }
        let mv = run_search(
            &handle,
            *pos,
            TimeControl::PerMove(Duration::from_millis(1)),
        )
        .unwrap_or_else(|| panic!("no SearchComplete for 1ms corpus[{i}]"));
        assert!(
            legal.contains(&mv),
            "engine returned illegal move {} on 1ms corpus[{i}] FEN {:?}",
            mv.to_long_algebraic(),
            pos.to_fen()
        );
    }
    let _ = handle.send(Command::Shutdown);
}
