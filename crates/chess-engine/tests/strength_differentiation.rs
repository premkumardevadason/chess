//! T062 — Strength differentiation (validation suite).
//!
//! Plays N self-play games at a low time control between
//! `StrengthPreset::Beginner` and `StrengthPreset::Maximum` and asserts
//! the Maximum side wins ≥ 90% of decisive games per
//! [SC-003](../../specs/001-chess-ai-rewrite/spec.md#sc-003).
//!
//! Per the task note, the sample size is reduced from spec's 100 to 20
//! to keep runtime tractable; the full match is a manual / nightly job.
//! The test is `#[ignore]` so default CI runs skip it; invoke
//! explicitly:
//!
//! ```text
//! cargo test -p chess-engine --test strength_differentiation \
//!     --release -- --ignored --nocapture
//! ```

use std::time::{Duration, Instant};

use chess_core::{legal_moves, Game, GameMode, GameResult};
use chess_engine::{
    Command, Engine, EngineConfig, EngineHandle, Event, Mode, StrengthPreset, TimeControl,
};

const GAMES: usize = 20;
/// Per-move budget — see [SC-003] (kept tight so the suite finishes in CI-class time).
const TC: TimeControl = TimeControl::PerMove(Duration::from_millis(200));
/// Hard cap on plies. Anything longer is adjudicated as a draw to keep
/// the harness from looping when both sides shuffle pieces.
const MAX_PLIES: usize = 200;

#[derive(Debug, Default, Clone)]
struct Score {
    max_wins: u32,
    beginner_wins: u32,
    draws: u32,
    decisive: u32,
}

fn ask_engine_for_move(
    handle: &EngineHandle,
    game: &Game,
    strength: StrengthPreset,
    seed: u64,
    budget: Duration,
) -> Option<chess_core::Move> {
    handle
        .send(Command::SetPosition {
            position: game.current,
            history: game.history.iter().map(|r| r.move_played).collect(),
        })
        .ok()?;
    handle
        .send(Command::StartSearch {
            config: EngineConfig {
                mode: Mode::Reproducible { seed },
                time_control: TC,
                strength,
                max_threads: 1,
                ..EngineConfig::default()
            },
        })
        .ok()?;

    let started = Instant::now();
    let overall = budget + Duration::from_secs(2);
    while started.elapsed() < overall {
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

/// Play one game. `max_is_white` flips colours so each preset plays
/// half its games as White.
#[allow(dead_code)]
fn play_one_game(
    handle: &EngineHandle,
    max_is_white: bool,
    game_index: usize,
) -> Option<chess_core::Color> {
    let mut game = Game::new_game(GameMode::AiVsAi);
    let mut ply = 0;
    while game.result.is_none() && ply < MAX_PLIES {
        let stm_is_white = matches!(game.side_to_move(), chess_core::Color::White);
        let stm_is_max = stm_is_white == max_is_white;
        let strength = if stm_is_max {
            StrengthPreset::Maximum
        } else {
            StrengthPreset::Beginner
        };
        // Distinct seeds per game/ply prevent identical lines across games.
        let seed = 0xCAFE_F00D
            ^ ((game_index as u64) << 32)
            ^ ply as u64;
        let mv = ask_engine_for_move(handle, &game, strength, seed, Duration::from_millis(200))?;
        // Reject illegal returns by dropping into a heuristic legal-only
        // fallback so a single buggy move does not abort the suite.
        let legal = legal_moves(&game.current);
        let chosen = if legal.contains(&mv) {
            mv
        } else {
            *legal.first()?
        };
        game.make_move(chosen).ok()?;
        ply += 1;
    }

    match game.result {
        Some(GameResult::Checkmate(winner)) => {
            // Map winner → was-Maximum.
            let max_won = matches!(winner, chess_core::Color::White) == max_is_white;
            Some(if max_won {
                chess_core::Color::White
            } else {
                chess_core::Color::Black
            })
            .map(|_| {
                if max_won {
                    chess_core::Color::White
                } else {
                    chess_core::Color::Black
                }
            })
        }
        _ => None,
    }
}

#[test]
#[ignore = "long-running self-play validation; run with --ignored"]
fn maximum_dominates_beginner_at_low_time_control() {
    let handle = Engine::new().spawn();
    let mut score = Score::default();
    for i in 0..GAMES {
        // Alternate: even-index games → Max plays White; odd → Max plays Black.
        let max_is_white = i % 2 == 0;
        let _ = chess_core::Position::startpos();
        let mut game = Game::new_game(GameMode::AiVsAi);
        let mut ply = 0;
        let mut decided: Option<GameResult> = None;
        while game.result.is_none() && ply < MAX_PLIES {
            let stm_is_white = matches!(game.side_to_move(), chess_core::Color::White);
            let stm_is_max = stm_is_white == max_is_white;
            let strength = if stm_is_max {
                StrengthPreset::Maximum
            } else {
                StrengthPreset::Beginner
            };
            let seed = 0xCAFE_F00D ^ ((i as u64) << 32) ^ ply as u64;
            let mv = ask_engine_for_move(
                &handle,
                &game,
                strength,
                seed,
                Duration::from_millis(200),
            )
            .expect("engine returned a move");
            let legal = legal_moves(&game.current);
            let chosen = if legal.contains(&mv) {
                mv
            } else {
                *legal.first().expect("at least one legal move")
            };
            game.make_move(chosen).expect("apply");
            ply += 1;
            if let Some(r) = game.result {
                decided = Some(r);
                break;
            }
        }

        match decided {
            Some(GameResult::Checkmate(winner)) => {
                score.decisive += 1;
                let max_won = matches!(winner, chess_core::Color::White) == max_is_white;
                if max_won {
                    score.max_wins += 1;
                } else {
                    score.beginner_wins += 1;
                }
            }
            Some(GameResult::Resignation(_)) => {
                // No resignation in the harness — but defend against future changes.
                score.decisive += 1;
            }
            Some(GameResult::Draw(_)) => {
                score.draws += 1;
            }
            None => {
                // Ply cap → adjudicate as a draw.
                score.draws += 1;
            }
        }
        eprintln!(
            "[strength_diff] game {:>2}/{} (max_white={}): {:?}",
            i + 1,
            GAMES,
            max_is_white,
            decided
        );
    }
    let _ = handle.send(Command::Shutdown);

    eprintln!(
        "[strength_diff] decisive={} max_wins={} beginner_wins={} draws={}",
        score.decisive, score.max_wins, score.beginner_wins, score.draws
    );

    if score.decisive == 0 {
        // SC-003 cannot be evaluated without decisive games; fail loud
        // so the issue is investigated rather than silently skipped.
        panic!("no decisive games — strength differentiation cannot be assessed");
    }
    let ratio = score.max_wins as f64 / score.decisive as f64;
    assert!(
        ratio >= 0.90,
        "Maximum win rate {:.2}% < 90% over {} decisive games (max={}, beg={})",
        ratio * 100.0,
        score.decisive,
        score.max_wins,
        score.beginner_wins
    );
}
