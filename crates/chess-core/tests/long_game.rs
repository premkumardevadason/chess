//! T107 — long-game stability test (closes analysis F9).
//!
//! Builds a synthetic 300-ply game using a 4-ply knight shuffle
//! (`Nf3 Nf6 Ng1 Ng8`) interleaved with a single pawn-push
//! "perturbation" every 6 plies so that no shuffle position is ever
//! visited a third time (threefold) and the half-move clock never
//! reaches 100 (50-move rule). Then iterates `Undo` back to ply 0
//! and `Redo` forward to ply 300 multiple times, asserting:
//!
//! 1. **No panic** at any step (drives `Game::undo` / `Game::redo`
//!    through every special-move-free path 300+ times).
//! 2. **Zobrist round-trip equality** at every undo→redo checkpoint
//!    (per [data-model.md cross-cutting invariant 1]).
//! 3. **Bounded allocator growth**: the backing `Vec` capacities for
//!    `history` + `redo_stack` + `repetition_table.len()` between
//!    iterations 2 and 5 must not differ by more than 5%, a
//!    dependency-free proxy for "no allocator growth between
//!    iterations 2 and 5 (within 5%)" called for by
//!    [Edge Cases](../../specs/001-chess-ai-rewrite/spec.md#edge-cases).
//!
//! [data-model.md cross-cutting invariant 1]: ../../specs/001-chess-ai-rewrite/data-model.md

use chess_core::{legal_moves, san::parse_san, Color, Game, GameMode};

/// Build a 300-ply game that does not auto-terminate. Returns the
/// game and the sequence of moves played (in order).
fn build_long_game() -> Game {
    let mut g = Game::new_game(GameMode::HumanVsAi(Color::White));
    // Per-colour 2-cycles for the knight shuffle; the right entry
    // is selected from the side-to-move so a single perturbation
    // (which adds an odd number of plies) does not desynchronise
    // the cycle.
    let white_cycle = ["Nf3", "Ng1"];
    let black_cycle = ["Nf6", "Ng8"];
    let mut white_shuffle_idx = 0usize;
    let mut black_shuffle_idx = 0usize;
    let target_plies = 300usize;

    while g.history.len() < target_plies {
        // Inject a deterministic perturbation every 6 plies so that
        // the repetition table never sees a shuffle position three
        // times and the half-move clock resets long before 100. The
        // perturbation is the lexicographically-smallest legal SAN
        // that is *not* a knight move (so it cannot accidentally be
        // the next shuffle move) and that does not end the game; we
        // search legal_moves for the first such move. This avoids
        // the bookkeeping needed to maintain a hand-curated push pool
        // that never collides with the knight shuffle squares.
        let inject_perturbation = g.history.len() % 6 == 4;
        let stm = g.current.side_to_move;
        let mv = if inject_perturbation {
            // Look ahead two plies — White and Black both need the
            // next shuffle move available after the perturbation
            // (perturbation is one ply, then the *other* colour
            // plays a shuffle, then we play our shuffle). We pick
            // the lex-smallest pawn move that keeps both shuffles
            // legal so the cycle never breaks.
            let next_white = white_cycle[white_shuffle_idx % white_cycle.len()];
            let next_black = black_cycle[black_shuffle_idx % black_cycle.len()];
            let mut best: Option<(String, chess_core::Move)> = None;
            for m in legal_moves(&g.current) {
                let san = chess_core::san::san_for(&g.current, m);
                let first = san.chars().next().unwrap_or(' ');
                let is_pawn_move = first.is_ascii_lowercase();
                let is_capture = san.contains('x');
                if !is_pawn_move && !is_capture {
                    continue;
                }
                let mut probe = g.clone();
                if probe.make_move(m).is_err() {
                    continue;
                }
                if probe.result().is_some() {
                    continue;
                }
                // After perturbation, the side whose shuffle is next
                // up must be able to play its scripted knight move,
                // and likewise for the following ply.
                let (first_san, second_san) = match probe.current.side_to_move {
                    Color::White => (next_white, next_black),
                    Color::Black => (next_black, next_white),
                };
                let Ok(first_mv) = parse_san(&probe.current, first_san) else { continue };
                let mut probe2 = probe.clone();
                if probe2.make_move(first_mv).is_err() {
                    continue;
                }
                if probe2.result().is_some() {
                    continue;
                }
                if parse_san(&probe2.current, second_san).is_err() {
                    continue;
                }
                match &best {
                    Some((cur, _)) if cur.as_str() <= san.as_str() => {}
                    _ => best = Some((san, m)),
                }
            }
            best
                .unwrap_or_else(|| panic!("no perturbation move at ply {}", g.history.len()))
                .1
        } else {
            let san = match stm {
                Color::White => {
                    let s = white_cycle[white_shuffle_idx % white_cycle.len()];
                    white_shuffle_idx += 1;
                    s
                }
                Color::Black => {
                    let s = black_cycle[black_shuffle_idx % black_cycle.len()];
                    black_shuffle_idx += 1;
                    s
                }
            };
            parse_san(&g.current, san)
                .unwrap_or_else(|e| panic!("parse {san} at ply {}: {e:?}", g.history.len()))
        };

        g.make_move(mv).unwrap_or_else(|e| {
            panic!("apply move at ply {}: {e:?}", g.history.len())
        });
        assert!(
            g.result().is_none(),
            "game terminated early at ply {} ({:?})",
            g.history.len(),
            g.result(),
        );
    }
    assert_eq!(g.history.len(), target_plies);
    g
}

#[test]
fn long_game_undo_redo_is_stable_and_bounded() {
    let mut g = build_long_game();
    let target_plies = g.history.len();
    let live_zobrist = g.current.zobrist;
    let start_zobrist = g.start_position.zobrist;

    // Snapshot Vec capacities + repetition-table size after each full
    // undo→redo iteration. Using `Vec::capacity()` plus
    // `HashMap::len()` is a dependency-free, deterministic proxy for
    // "the data structure is not growing".
    #[derive(Copy, Clone, Debug, PartialEq)]
    struct Footprint {
        history_cap: usize,
        redo_cap: usize,
        rep_len: usize,
    }
    fn snapshot(g: &Game) -> Footprint {
        Footprint {
            history_cap: g.history.capacity(),
            redo_cap: g.redo_stack.capacity(),
            rep_len: g.repetition_table.len(),
        }
    }

    let mut footprints = Vec::<Footprint>::with_capacity(5);

    for iter in 1..=5 {
        // Undo all the way back to the start position.
        while !g.history.is_empty() {
            g.undo()
                .unwrap_or_else(|e| panic!("iter {iter}: undo: {e:?}"));
        }
        assert_eq!(g.history.len(), 0, "iter {iter}: undo did not reach ply 0");
        assert_eq!(
            g.current.zobrist, start_zobrist,
            "iter {iter}: zobrist mismatch at ply 0",
        );
        assert_eq!(
            g.redo_stack.len(),
            target_plies,
            "iter {iter}: redo_stack should hold every undone ply",
        );

        // Redo all the way back to live.
        while g.redo().is_ok() {}
        assert_eq!(
            g.history.len(),
            target_plies,
            "iter {iter}: redo did not reach ply {target_plies}",
        );
        assert_eq!(
            g.current.zobrist, live_zobrist,
            "iter {iter}: zobrist mismatch at ply {target_plies}",
        );

        footprints.push(snapshot(&g));
    }

    // Iterations 2..=5 must stay within 5% of iteration 2's footprint.
    let baseline = footprints[1]; // iter 2 (zero-indexed)
    for (i, fp) in footprints.iter().enumerate().skip(1) {
        let iter = i + 1;
        for (label, baseline_v, current_v) in [
            ("history.capacity", baseline.history_cap, fp.history_cap),
            ("redo_stack.capacity", baseline.redo_cap, fp.redo_cap),
            ("repetition_table.len", baseline.rep_len, fp.rep_len),
        ] {
            if baseline_v == 0 {
                assert_eq!(
                    current_v, 0,
                    "iter {iter}: {label} grew from 0 to {current_v}",
                );
                continue;
            }
            let allowed = baseline_v
                .saturating_mul(105)
                .saturating_div(100)
                .max(baseline_v + 1);
            assert!(
                current_v <= allowed,
                "iter {iter}: {label} grew beyond 5% (baseline {baseline_v}, now {current_v}, allowed {allowed})",
            );
        }
    }
}
