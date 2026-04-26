//! Compare our movegen's divide-perft output against `cozy-chess` (a
//! well-validated Rust chess library), to localize discrepancies.
//!
//! Usage: `cargo run --release -p chess-core --example compare_divide -- "<FEN>" <depth>`

use std::collections::BTreeMap;
use std::str::FromStr;

use chess_core::{legal_moves, Position};
use cozy_chess::{Board, Move as CzMove};

fn ours_perft(pos: &Position, depth: u32) -> u64 {
    if depth == 0 {
        return 1;
    }
    let mut nodes = 0u64;
    for mv in legal_moves(pos) {
        let (next, _) = pos.make_move(mv);
        nodes += ours_perft(&next, depth - 1);
    }
    nodes
}

fn cozy_perft(b: &Board, depth: u32) -> u64 {
    if depth == 0 {
        return 1;
    }
    let mut nodes = 0u64;
    b.generate_moves(|moves| {
        for mv in moves {
            let mut child = b.clone();
            child.play(mv);
            nodes += cozy_perft(&child, depth - 1);
        }
        false
    });
    nodes
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let fen = args.get(1).cloned().unwrap_or_else(|| {
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2pP/R2Q1RK1 w kq - 0 1".to_string()
    });
    let depth: u32 = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(2);

    let p = Position::from_fen(&fen).expect("valid FEN (ours)");
    let b = Board::from_fen(&fen, false).expect("valid FEN (cozy)");

    let mut ours: BTreeMap<String, u64> = BTreeMap::new();
    for mv in legal_moves(&p) {
        let (next, _) = p.make_move(mv);
        let count = ours_perft(&next, depth - 1);
        ours.insert(mv.to_long_algebraic(), count);
    }

    let mut theirs: BTreeMap<String, u64> = BTreeMap::new();
    b.generate_moves(|moves| {
        for mv in moves {
            let mut child = b.clone();
            child.play(mv);
            let count = cozy_perft(&child, depth - 1);
            theirs.insert(mv.to_string(), count);
        }
        false
    });

    let mut all_keys: Vec<String> = ours.keys().chain(theirs.keys()).cloned().collect();
    all_keys.sort();
    all_keys.dedup();

    let mut ours_total = 0u64;
    let mut theirs_total = 0u64;
    println!("{:8}  {:>8}  {:>8}  diff", "move", "ours", "cozy");
    for k in all_keys {
        let o = ours.get(&k).copied().unwrap_or(0);
        let t = theirs.get(&k).copied().unwrap_or(0);
        ours_total += o;
        theirs_total += t;
        let mark = if o == t { "" } else { " <<<" };
        println!(
            "{:8}  {:>8}  {:>8}  {}{}",
            k,
            o,
            t,
            (o as i64 - t as i64),
            mark
        );
    }
    println!();
    println!(
        "TOTAL ours = {}, cozy = {}, diff = {}",
        ours_total,
        theirs_total,
        ours_total as i64 - theirs_total as i64
    );

    let _ = CzMove::from_str("a1a2");
}
