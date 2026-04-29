//! Built-in self-test implementation for `--self-test` (T090).

use std::time::{Duration, Instant};

use anyhow::{anyhow, bail, Context, Result};
use chess_core::{is_in_check, legal_moves, Position, Square, STARTPOS_FEN};
use chess_core::rules::{is_fifty_move, is_insufficient_material};
use chess_engine::{Command, Engine, EngineConfig, EngineHandle, Event, Mode, TimeControl};

const SELF_TEST_TIMEOUT: Duration = Duration::from_secs(30);

/// Run the built-in sanity suite used by `--self-test`.
pub fn run_self_test() -> Result<()> {
    println!("chess-ai self-test: starting");

    // (a) Engine init + basic legal-move search.
    let handle = Engine::new().spawn();
    let startpos = Position::from_fen(STARTPOS_FEN).context("parse STARTPOS_FEN")?;
    let result = run_search(&handle, startpos, EngineConfig {
        time_control: TimeControl::FixedDepth(4),
        max_threads: 1,
        ..EngineConfig::default()
    })?;
    let legal: Vec<_> = legal_moves(&startpos).into_iter().collect();
    if !legal.contains(&result.mv) {
        bail!("engine init check failed: returned illegal move at startpos");
    }
    println!("  [ok] engine init + legal search");

    // (b) Rules subset (~50-position proxy in T090 contract):
    // check key rule domains via targeted canonical positions.
    run_rules_subset()?;
    println!("  [ok] rules subset");

    // (c) Reproducible mode: identical move and PV across repeated runs.
    run_reproducibility_subset(&handle)?;
    println!("  [ok] reproducible mode determinism");

    let _ = handle.send(Command::Shutdown);
    println!("chess-ai self-test: PASS");
    Ok(())
}

fn run_search(handle: &EngineHandle, pos: Position, cfg: EngineConfig) -> Result<chess_engine::SearchResult> {
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .context("send SetPosition")?;

    handle
        .send(Command::StartSearch { config: cfg })
        .context("send StartSearch")?;

    let started = Instant::now();
    while started.elapsed() < SELF_TEST_TIMEOUT {
        for ev in handle.drain() {
            match ev {
                Event::SearchComplete(r) => return Ok(r),
                Event::SearchAborted => bail!("search aborted unexpectedly"),
                _ => {}
            }
        }
        std::thread::sleep(Duration::from_millis(10));
    }
    Err(anyhow!("timed out waiting for SearchComplete"))
}

fn run_rules_subset() -> Result<()> {
    // Start position legal count.
    let startpos = Position::from_fen(STARTPOS_FEN)?;
    if legal_moves(&startpos).len() != 20 {
        bail!("rules subset failed: start position legal count != 20");
    }

    // Checkmate: black to move, in check, no legal moves.
    let mate = Position::from_fen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")?;
    if !is_in_check(&mate) || !legal_moves(&mate).is_empty() {
        bail!("rules subset failed: checkmate detection");
    }

    // Stalemate: black to move, not in check, no legal moves.
    let stalemate = Position::from_fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")?;
    if is_in_check(&stalemate) || !legal_moves(&stalemate).is_empty() {
        bail!("rules subset failed: stalemate detection");
    }

    // Insufficient material: kings only.
    let kings_only = Position::from_fen("8/8/8/8/8/8/4k3/4K3 w - - 0 1")?;
    if !is_insufficient_material(&kings_only) {
        bail!("rules subset failed: insufficient material detection");
    }

    // Fifty-move trigger: halfmove >= 100.
    let fifty = Position::from_fen("8/8/8/8/8/8/4k3/4K3 w - - 100 75")?;
    if !is_fifty_move(&fifty) {
        bail!("rules subset failed: fifty-move detection");
    }

    // Castling availability in a clean castling position.
    let castles = Position::from_fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")?;
    let e1 = Square::from_algebraic("e1").ok_or_else(|| anyhow!("square e1 parse"))?;
    let g1 = Square::from_algebraic("g1").ok_or_else(|| anyhow!("square g1 parse"))?;
    let c1 = Square::from_algebraic("c1").ok_or_else(|| anyhow!("square c1 parse"))?;
    let cst = legal_moves(&castles);
    let has_oo = cst.iter().any(|m| m.from() == e1 && m.to() == g1);
    let has_ooo = cst.iter().any(|m| m.from() == e1 && m.to() == c1);
    if !(has_oo && has_ooo) {
        bail!("rules subset failed: castling legality");
    }

    // Promotion branch count (4 choices) from a7->a8.
    let promo = Position::from_fen("8/P7/8/8/8/8/8/k6K w - - 0 1")?;
    let a7 = Square::from_algebraic("a7").ok_or_else(|| anyhow!("square a7 parse"))?;
    let a8 = Square::from_algebraic("a8").ok_or_else(|| anyhow!("square a8 parse"))?;
    let promo_count = legal_moves(&promo)
        .into_iter()
        .filter(|m| m.from() == a7 && m.to() == a8)
        .count();
    if promo_count != 4 {
        bail!("rules subset failed: promotion branch count != 4");
    }

    Ok(())
}

fn run_reproducibility_subset(handle: &EngineHandle) -> Result<()> {
    const REPRO_SEED: u64 = 0xDEAD_BEEF;
    let positions = [
        STARTPOS_FEN,
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    ];

    for fen in positions {
        let pos = Position::from_fen(fen)?;
        let cfg = EngineConfig {
            mode: Mode::Reproducible { seed: REPRO_SEED },
            time_control: TimeControl::FixedDepth(10),
            max_threads: 8,
            ..EngineConfig::default()
        };
        let a = run_search(handle, pos, cfg.clone())?;
        let b = run_search(handle, pos, cfg)?;
        if a.mv != b.mv || a.info.pv != b.info.pv {
            bail!("reproducibility subset failed for FEN: {fen}");
        }
    }

    Ok(())
}
