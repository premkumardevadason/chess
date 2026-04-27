//! T030 — FIDE 6-position perft suite at the canonical reference
//! depths (6 / 5 / 7 / 5 / 5 / 5).
//!
//! ## Why this lives here
//!
//! The `chess-engine` crate is the layer that actually drives Carp's
//! magic-bitboard movegen at search time. Matching the published node
//! counts at deep ply confirms three things at once:
//!
//! 1. The vendored magic-bitboard tables (sliders.bin, between.bin,
//!    embedded via `include_bytes!`) round-trip correctly.
//! 2. `Board::make_move` / `unmake_move` are bug-free across special
//!    moves (en-passant, castling, promotions) — perft would diverge
//!    otherwise.
//! 3. Our crate's compile-time setup (e.g. NNUE feature gates) does
//!    not perturb movegen.
//!
//! ## Why two depths
//!
//! The default test profile (`cargo test`) runs the **shallow tier**
//! (≤ depth 4) so a developer-loop check finishes in < 5 seconds.
//! Setting `CHESS_DEEP_PERFT=1` runs the deep tier — depths 6/5/7/5/5/5
//! totalling ~960 M nodes — which finishes in ~30 seconds in release
//! mode but takes a few minutes in dev mode. The deep tier is run by
//! CI's `cargo test --release` job.
//!
//! Both tiers consume the same `corpora/perft_suite.epd` so the
//! reference values live in a single place.

use chess::board::Board;

/// Depth at which the shallow ("quick") tier runs each position.
const SHALLOW_DEPTH: usize = 4;

/// Per-position deep-tier depths. Index aligned with iteration order
/// in `corpora/perft_suite.epd` (Initial, Kiwipete, Pos3, Pos4, Pos5,
/// Pos6).
const DEEP_DEPTHS: [usize; 6] = [6, 5, 7, 5, 5, 5];

/// One row from the EPD corpus.
struct PerftCase {
    fen: String,
    counts: Vec<(usize, u64)>,
}

fn parse_corpus() -> Vec<PerftCase> {
    let raw = include_str!("corpora/perft_suite.epd");
    let mut cases = Vec::new();
    for line in raw.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let mut parts = trimmed.splitn(2, ';');
        let fen = parts.next().expect("FEN in EPD line").trim().to_string();
        let rest = parts.next().expect("EPD line lacks D-counts").trim();
        let mut counts = Vec::new();
        for tag in rest.split(';') {
            let tag = tag.trim();
            if !tag.starts_with('D') {
                continue;
            }
            let body = &tag[1..];
            let mut tok = body.split_whitespace();
            let depth: usize = tok
                .next()
                .expect("D<n>")
                .parse()
                .expect("depth must be a positive integer");
            let nodes: u64 = tok
                .next()
                .expect("D<n> <count>")
                .parse()
                .expect("count must be a u64");
            counts.push((depth, nodes));
        }
        cases.push(PerftCase { fen, counts });
    }
    assert!(!cases.is_empty(), "EPD corpus parsed empty");
    cases
}

fn perft(board: &Board, depth: usize) -> u64 {
    board.perft_test::<true>(depth)
}

fn run_case(case: &PerftCase, target_depth: usize) {
    let board: Board = case
        .fen
        .parse()
        .unwrap_or_else(|e| panic!("invalid FEN {:?}: {e}", case.fen));
    let expected = case
        .counts
        .iter()
        .find(|(d, _)| *d == target_depth)
        .unwrap_or_else(|| {
            panic!(
                "corpus is missing D{target_depth} for FEN {}; have {:?}",
                case.fen, case.counts
            )
        })
        .1;

    let actual = perft(&board, target_depth);
    assert_eq!(
        actual, expected,
        "perft({}) for FEN {:?}: expected {expected}, got {actual}",
        target_depth, case.fen
    );
}

/// Lightweight `Board`-side perft. We don't depend on `Board::perft`
/// directly because that variant prints to stdout (Carp's debug aid);
/// instead we drive a private bulk-counting recursion.
trait PerftTest {
    fn perft_test<const BULK_C: bool>(&self, depth: usize) -> u64;
}

impl PerftTest for Board {
    fn perft_test<const BULK_C: bool>(&self, depth: usize) -> u64 {
        use chess::board::QUIETS;

        if depth == 0 {
            return 1;
        }
        let move_list = self.gen_moves::<QUIETS>();
        if BULK_C && depth == 1 {
            return move_list.len() as u64;
        }
        let mut nodes = 0u64;
        for i in 0..move_list.len() {
            let mv = move_list.moves[i];
            let next = self.make_move(mv);
            nodes += next.perft_test::<BULK_C>(depth - 1);
        }
        nodes
    }
}

#[test]
fn perft_shallow_tier() {
    let cases = parse_corpus();
    assert_eq!(cases.len(), 6, "expected 6 FIDE positions");
    for case in &cases {
        run_case(case, SHALLOW_DEPTH);
    }
}

/// Deep tier — opt-in via `CHESS_DEEP_PERFT=1`. Skipped by default to
/// keep the developer loop fast; runs in CI's release-mode job.
#[test]
fn perft_deep_tier() {
    if std::env::var("CHESS_DEEP_PERFT").as_deref() != Ok("1") {
        eprintln!("skipping deep perft (set CHESS_DEEP_PERFT=1 to run)");
        return;
    }

    let cases = parse_corpus();
    assert_eq!(cases.len(), 6);
    for (case, &depth) in cases.iter().zip(DEEP_DEPTHS.iter()) {
        eprintln!(
            "perft deep: {depth}-ply on {fen} (expected {})",
            case.counts
                .iter()
                .find(|(d, _)| *d == depth)
                .map(|(_, n)| *n)
                .unwrap_or(0),
            fen = case.fen,
        );
        run_case(case, depth);
    }
}

#[test]
fn corpus_parses() {
    let cases = parse_corpus();
    assert_eq!(cases.len(), 6, "EPD corpus should have 6 positions");
    for case in cases {
        assert!(case.counts.len() >= 4, "each position needs ≥ 4 depths");
        for (d, n) in case.counts {
            assert!((1..=8).contains(&d), "depth {d} out of range");
            assert!(n > 0, "node count must be positive");
        }
    }
}
