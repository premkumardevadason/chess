//! T083 — Reproducible-mode determinism contract.
//!
//! For 50 deterministic positions (including Kiwipete), run two
//! searches with identical `Mode::Reproducible { seed }` config and
//! assert both best move and PV are bit-identical.

use std::time::{Duration, Instant};

use chess_core::{legal_moves, Position};
use chess_engine::{Command, Engine, EngineConfig, EngineHandle, Event, Mode, TimeControl};

const SEED: u64 = 0xDEAD_BEEF;

const SEED_FENS: &[&str] = &[
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    // Kiwipete
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2pP/R2Q1RK1 w kq - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
];

struct Xoshiro256 {
    s: [u64; 4],
}

impl Xoshiro256 {
    fn new(seed: u64) -> Self {
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

    fn below(&mut self, n: usize) -> usize {
        (self.next_u64() % n as u64) as usize
    }
}

fn build_corpus() -> Vec<Position> {
    let mut rng = Xoshiro256::new(SEED);
    let mut out = Vec::with_capacity(50);
    for fen in SEED_FENS {
        out.push(Position::from_fen(fen).expect("valid seed FEN"));
    }

    while out.len() < 50 {
        let parent = out[rng.below(out.len())];
        let mut pos = parent;
        let hops = 1 + rng.below(5);
        for _ in 0..hops {
            let moves = legal_moves(&pos);
            if moves.is_empty() {
                break;
            }
            pos = pos.make_move(moves[rng.below(moves.len())]).0;
        }
        out.push(pos);
    }

    out
}

fn run_search(handle: &EngineHandle, pos: Position, seed: u64) -> chess_engine::SearchResult {
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .expect("set position");

    handle
        .send(Command::StartSearch {
            config: EngineConfig {
                mode: Mode::Reproducible { seed },
                time_control: TimeControl::FixedDepth(10),
                max_threads: 8,
                ..EngineConfig::default()
            },
        })
        .expect("start search");

    let started = Instant::now();
    let timeout = Duration::from_secs(30);
    while started.elapsed() < timeout {
        for event in handle.drain() {
            match event {
                Event::SearchComplete(result) => return result,
                Event::SearchAborted => panic!("search aborted unexpectedly"),
                _ => {}
            }
        }
        std::thread::sleep(Duration::from_millis(5));
    }

    panic!("timed out waiting for SearchComplete");
}

#[test]
fn reproducible_mode_returns_identical_move_and_pv() {
    let corpus = build_corpus();
    assert_eq!(corpus.len(), 50);

    let handle = Engine::new().spawn();

    for (i, pos) in corpus.iter().enumerate() {
        if legal_moves(pos).is_empty() {
            continue;
        }

        let first = run_search(&handle, *pos, SEED);
        let second = run_search(&handle, *pos, SEED);

        assert_eq!(
            first.mv, second.mv,
            "best move mismatch at corpus[{i}] FEN {}",
            pos.to_fen()
        );
        assert_eq!(
            first.info.pv, second.info.pv,
            "PV mismatch at corpus[{i}] FEN {}",
            pos.to_fen()
        );
    }

    let _ = handle.send(Command::Shutdown);
}
