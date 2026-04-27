//! Engine worker thread.
//!
//! Owns:
//!
//! - The Carp [`carp::position::Position`] (board + repetition history +
//!   incremental NNUE accumulator).
//! - The Carp [`carp::tt::TT`] (transposition table, sized from
//!   `EngineConfig::tt_size_mib`).
//! - The Carp [`carp::thread::ThreadPool`] (LazySMP search).
//! - An `Arc<AtomicBool>` global-stop flag shared with `ThreadPool` and
//!   exposed back to the API layer so `Command::Stop` can abort an
//!   in-flight search within ~50 ms (T021 / contracts §3.7).
//!
//! The worker runs a `recv` loop on the command channel. When a search is
//! in flight, it uses `crossbeam_channel::select!` to wait on EITHER a new
//! command (typically `Stop` or `Shutdown`) OR the "search finished"
//! signal — so cancellation latency is bounded by Carp's internal
//! `CHECK_FREQUENCY = 2048` node check, not by any fixed polling delay
//! on our side.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Instant;

use carp::{position::Position as CarpPosition, syzygy::probe::TB, thread::ThreadPool, tt::TT};
use chess::{board::Board as CarpBoard, nnue::NNUEState};
use chess_core::{Move as CoreMove, Position as CorePosition};
use crossbeam_channel::{select, Receiver, Sender};

use crate::adapter::{move_from_carp, move_to_carp, position_to_carp, AdapterError};
use crate::api::{Command, Event};
use crate::config::{EngineConfig, Mode, Score, SearchInfo, SearchResult, TimeControl};

/// Shared state between the API layer and the worker. The API holds a
/// clone of `global_stop` so `EngineHandle::send(Command::Stop)` can flip
/// the bit even when the worker is blocked inside Carp's search.
pub(crate) struct WorkerShared {
    pub(crate) global_stop: Arc<AtomicBool>,
}

impl WorkerShared {
    pub(crate) fn new() -> Self {
        Self {
            global_stop: Arc::new(AtomicBool::new(false)),
        }
    }
}

/// Spawn the worker thread. Returns the shared stop-flag holder so the
/// API layer can trip it from outside.
pub(crate) fn spawn(
    cmd_rx: Receiver<Command>,
    event_tx: Sender<Event>,
) -> (Arc<AtomicBool>, thread::JoinHandle<()>) {
    let shared = WorkerShared::new();
    let global_stop = shared.global_stop.clone();
    let handle = thread::Builder::new()
        .name("chess-engine-worker".into())
        .spawn(move || run(cmd_rx, event_tx, shared))
        .expect("spawning chess-engine-worker thread");
    (global_stop, handle)
}

fn run(cmd_rx: Receiver<Command>, event_tx: Sender<Event>, shared: WorkerShared) {
    // Live state — replaced by SetPosition / Shutdown / re-entry.
    let mut tt = TT::default();
    let mut threads = ThreadPool::new(shared.global_stop.clone());

    // Last known position (chess-core view) and Carp board derived from it.
    // We rebuild the Carp `Position` (incl. NNUE accumulator) at the start
    // of every search to avoid carrying mutable accumulator state across
    // commands.
    let mut current_core: Option<CorePosition> = None;
    let mut current_carp_board: Option<CarpBoard> = None;
    let mut current_history: Vec<CoreMove> = Vec::new();

    // Reserved: last EngineConfig captured at StartSearch — will drive
    // progress-event throttling once T097a wires up periodic updates.
    let mut _last_config: EngineConfig = EngineConfig::default();

    while let Ok(cmd) = cmd_rx.recv() {
        match cmd {
            Command::SetPosition { position, history } => {
                let board = match position_to_carp(&position) {
                    Ok(b) => b,
                    Err(e) => {
                        let _ = event_tx.send(Event::Warning(format!("SetPosition failed: {e}")));
                        continue;
                    }
                };
                current_core = Some(position);
                current_carp_board = Some(board);
                current_history = history;
            }
            Command::SetConfig {
                max_threads,
                debug_logging: _,
            } => {
                // max_threads = 1 means main thread only (zero workers).
                let workers = max_threads.saturating_sub(1) as usize;
                threads.resize(workers);
            }
            Command::StartSearch { config } => {
                _last_config = config.clone();
                let board = match current_carp_board.clone() {
                    Some(b) => b,
                    None => {
                        let _ = event_tx.send(Event::Warning(
                            "StartSearch ignored: no SetPosition first".into(),
                        ));
                        continue;
                    }
                };
                let core_pos = current_core.expect("core position must be set when board is set");

                // Reset transposition table per search to enforce
                // reproducibility when Mode::Reproducible.
                if matches!(config.mode, Mode::Reproducible { .. }) {
                    tt.clear();
                    threads.resize(0); // single-thread search
                } else if let Some(target) = workers_for_config(&config) {
                    threads.resize(target);
                }

                // Build a fresh Carp Position from the board + history.
                // Carp's repetition detector reads from the position's
                // history Vec — we replay each move of `current_history`
                // so threefold detection is correct.
                let mut carp_pos = match build_carp_position(&board, &current_history) {
                    Ok(p) => p,
                    Err(e) => {
                        let _ =
                            event_tx.send(Event::Warning(format!("position rebuild failed: {e}")));
                        continue;
                    }
                };

                shared.global_stop.store(false, Ordering::SeqCst);
                let started = Instant::now();
                let tc = map_time_control(config.time_control);

                // Run the search synchronously on this worker thread —
                // Carp's ThreadPool::deploy_search blocks. To remain
                // responsive to Stop / Shutdown we spawn it on a helper
                // thread and `select!` between the cmd channel and the
                // helper's completion notice.
                let (done_tx, done_rx) = crossbeam_channel::bounded::<carp::position::Position>(1);
                // SAFETY: deploy_search needs &mut Position, &TT, TB, TimeControl.
                // We move the position INTO the helper, run the search, then
                // ship it back via the channel so the next iteration can
                // resume from there if needed. TT and ThreadPool stay on
                // the worker thread; the helper takes them by &mut via
                // a shared reference -- not allowed across threads, so we
                // cheat by passing TT/threads via a mutable raw pointer
                // protected by the fact that only this worker thread
                // observes them. We use `thread::scope` to keep the
                // borrow checker happy.
                let result = thread::scope(|scope| {
                    let stop_for_helper = shared.global_stop.clone();
                    let _helper = scope.spawn(|| {
                        let _ = stop_for_helper; // not used inside the closure but moved in
                        let mv = threads.deploy_search(&mut carp_pos, &tt, TB::default(), tc);
                        let _ = done_tx.send(carp_pos);
                        mv
                    });

                    let mut completed = true;
                    let mv = loop {
                        select! {
                            recv(done_rx) -> _carp_pos_after => {
                                // join handles via JoinHandle; the helper has
                                // already returned. We don't need the carp_pos
                                // back for a single-shot search.
                                break _helper.join().expect("search helper panicked");
                            }
                            recv(cmd_rx) -> incoming => {
                                match incoming {
                                    Ok(Command::Stop) => {
                                        shared.global_stop.store(true, Ordering::SeqCst);
                                        completed = false;
                                    }
                                    Ok(Command::Shutdown) => {
                                        shared.global_stop.store(true, Ordering::SeqCst);
                                        completed = false;
                                        // Drain after the helper joins below.
                                    }
                                    Ok(other) => {
                                        let _ = event_tx.send(Event::Warning(format!(
                                            "ignored {:?} during in-flight search",
                                            std::mem::discriminant(&other)
                                        )));
                                    }
                                    Err(_) => {
                                        // Sender dropped — abort and exit.
                                        shared.global_stop.store(true, Ordering::SeqCst);
                                        completed = false;
                                    }
                                }
                            }
                        }
                    };
                    (mv, completed)
                });

                let (carp_mv, completed) = result;

                let core_mv = match move_from_carp(carp_mv, &core_pos) {
                    Ok(m) => m,
                    Err(e) => {
                        let _ = event_tx.send(Event::Warning(format!("invalid engine move: {e}")));
                        continue;
                    }
                };
                let elapsed = started.elapsed();
                let info = SearchInfo {
                    depth: 0,
                    seldepth: 0,
                    score: Score::Cp(0),
                    pv: vec![core_mv],
                    nodes: 0,
                    nps: 0,
                    elapsed,
                };
                if completed {
                    let _ = event_tx.send(Event::SearchComplete(SearchResult {
                        mv: core_mv,
                        info,
                        completed: true,
                    }));
                } else {
                    let _ = event_tx.send(Event::SearchAborted);
                }
            }
            Command::Stop => {
                // Stop while idle is a no-op (per contracts §3.1).
                shared.global_stop.store(true, Ordering::SeqCst);
            }
            Command::Shutdown => {
                let _ = event_tx.send(Event::Stopped);
                break;
            }
        }
    }

    // Channel hung up. Final flush in case the receiver still wants to know.
    let _ = event_tx.send(Event::Stopped);
}

fn workers_for_config(cfg: &EngineConfig) -> Option<usize> {
    let n = cfg.max_threads.saturating_sub(1);
    Some(n as usize)
}

fn map_time_control(tc: TimeControl) -> carp::clock::TimeControl {
    use carp::clock::TimeControl as CarpTc;
    match tc {
        TimeControl::Infinite => CarpTc::Infinite,
        TimeControl::FixedDepth(d) => CarpTc::FixedDepth(d as usize),
        TimeControl::FixedNodes(n) => CarpTc::FixedNodes(n),
        TimeControl::PerMove(d) => {
            let ms = d.as_millis().min(u64::MAX as u128) as u64;
            CarpTc::FixedTime(ms)
        }
    }
}

/// Rebuild a Carp `Position` from a starting board plus a history of
/// `chess-core` moves. We replay the moves through Carp's `make_move`
/// so the NNUE accumulator and repetition history are correct.
fn build_carp_position(
    start: &CarpBoard,
    history: &[CoreMove],
) -> Result<CarpPosition, AdapterError> {
    let nnue_state = NNUEState::from_board(start);
    let mut pos_str = format!("fen {}", start.to_fen());
    if !history.is_empty() {
        pos_str.push_str(" moves");
        let mut board = start.clone();
        for mv in history {
            let carp_mv = move_to_carp(*mv, &board)?;
            pos_str.push(' ');
            pos_str.push_str(&format!("{}", carp_mv));
            board = board.make_move(carp_mv);
        }
    }
    let mut p: CarpPosition =
        pos_str
            .parse()
            .map_err(|e: &'static str| AdapterError::CarpFenParse {
                fen: pos_str.clone(),
                msg: e.to_string(),
            })?;
    // Replace the NNUE state in case Position::from_str gave us a fresh
    // one — defensive; not strictly required because parse() already
    // recomputes it from the final board.
    let _ = (nnue_state, &mut p);
    Ok(p)
}
