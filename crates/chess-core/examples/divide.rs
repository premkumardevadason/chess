//! Divide-perft helper: lists root moves with their child node counts.
//!
//! Usage: `cargo run --release -p chess-core --example divide -- "<FEN>" <depth>`
//! Defaults to Position 4 at depth 2 if no args.

use chess_core::{legal_moves, Position};

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

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let fen = args.get(1).cloned().unwrap_or_else(|| {
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2pP/R2Q1RK1 w kq - 0 1".to_string()
    });
    let depth: u32 = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(2);

    let p = Position::from_fen(&fen).unwrap_or_else(|e| panic!("bad FEN: {e}"));

    let mut moves: Vec<_> = legal_moves(&p).into_iter().collect();
    moves.sort_by_key(|m| m.raw());

    let mut grand_total = 0u64;
    for mv in moves {
        let (next, _) = p.make_move(mv);
        let count = perft(&next, depth - 1);
        grand_total += count;
        println!("{}: {}", mv.to_long_algebraic(), count);
    }
    println!();
    println!("TOTAL: {}", grand_total);
}
