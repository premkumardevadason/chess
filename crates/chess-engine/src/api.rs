//! Public engine API — `Engine`, `EngineHandle`, `Command`, `Event`,
//! `EngineDead`. Implements task T019 of the CP-C plan.
//!
//! See [contracts/engine-api.md] for the full behavioural contract.
//!
//! [contracts/engine-api.md]:
//! ../../specs/001-chess-ai-rewrite/contracts/engine-api.md

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread::JoinHandle;

use chess_core::{Move, Position};
use crossbeam_channel::{bounded, Receiver, Sender};

use crate::config::{EngineConfig, SearchInfo, SearchResult};
use crate::worker;

/// Channel capacity for the command queue. The contract specifies a
/// bounded channel of size 8.
const COMMAND_CHANNEL_CAP: usize = 8;

/// Channel capacity for the event queue. We pick something generous so the
/// worker can buffer SearchProgress events when the UI is slow to drain.
const EVENT_CHANNEL_CAP: usize = 256;

/// Engine factory. Construct one, call [`Engine::spawn`] to get a
/// channel-based [`EngineHandle`].
pub struct Engine {
    /// Reserved for future per-engine resources (e.g., separate net handles
    /// when running multiple engines side-by-side). Empty in v1.
    _private: (),
}

impl Engine {
    /// Construct an engine. Loads the embedded NNUE network into RAM by
    /// touching the `chess::nnue::NNUEState` static. Cost: ~50–150 ms;
    /// allocates ~30 MB for the network and accumulator.
    pub fn new() -> Self {
        // Touch the NNUE network and movegen tables to force the static
        // initialisation now (instead of lazily on the first search).
        // `chess::board::Board::default()` is cheap and pulls in:
        //   - The magic-bitboard tables (sliders.bin, between.bin, etc.).
        //   - The NNUE network (net.bin) when an NNUEState is built.
        let _ = chess::board::Board::default();
        let _ = chess::nnue::NNUEState::from_board(&chess::board::Board::default());
        Self { _private: () }
    }

    /// Set up a worker thread and return a controller. Engine state is
    /// owned by the worker thread; this handle communicates via channels.
    pub fn spawn(self) -> EngineHandle {
        let (cmd_tx, cmd_rx) = bounded::<Command>(COMMAND_CHANNEL_CAP);
        let (event_tx, event_rx) = bounded::<Event>(EVENT_CHANNEL_CAP);

        let (global_stop, join) = worker::spawn(cmd_rx, event_tx);

        EngineHandle {
            cmd_tx,
            event_rx,
            global_stop,
            join: Some(join),
            shutdown_signaled: AtomicBool::new(false),
        }
    }
}

impl Default for Engine {
    fn default() -> Self {
        Self::new()
    }
}

/// Channel-based controller for an engine running on a worker thread.
///
/// The handle is `!Sync` (it owns the only `Sender<Command>` and the only
/// `Receiver<Event>`); pass it through `Arc<Mutex<_>>` if multiple UI
/// threads need access. In the v1 architecture only the egui UI thread
/// holds this handle.
pub struct EngineHandle {
    cmd_tx: Sender<Command>,
    event_rx: Receiver<Event>,
    /// Cloned from the worker thread so the API can flip the stop flag
    /// without going through the bounded command channel (which might be
    /// momentarily full if the UI is spamming commands). Sets ≤ 50 ms
    /// abort latency per contracts §3.7.
    global_stop: Arc<AtomicBool>,
    join: Option<JoinHandle<()>>,
    shutdown_signaled: AtomicBool,
}

impl EngineHandle {
    /// Send a command. Non-blocking; bounded channel of size 8.
    /// Returns `Err` if the engine thread has already exited.
    pub fn send(&self, cmd: Command) -> Result<(), EngineDead> {
        // Stop is given a fast-path: flip the stop flag immediately AND
        // enqueue the command (so the worker can transition to "idle" and
        // emit `SearchAborted`). This satisfies contracts §3.7.
        if matches!(cmd, Command::Stop) {
            self.global_stop.store(true, Ordering::SeqCst);
        }
        self.cmd_tx.send(cmd).map_err(|_| EngineDead)
    }

    /// Try to receive an event without blocking.
    pub fn try_recv(&self) -> Option<Event> {
        self.event_rx.try_recv().ok()
    }

    /// Drain all pending events. Called by the UI on each frame.
    pub fn drain(&self) -> Vec<Event> {
        let mut out = Vec::new();
        while let Ok(ev) = self.event_rx.try_recv() {
            out.push(ev);
        }
        out
    }

    /// Block waiting for the next event. For tests / synchronous callers.
    pub fn recv(&self) -> Option<Event> {
        self.event_rx.recv().ok()
    }

    /// Send a `Shutdown` and join the worker thread. Idempotent.
    pub fn shutdown(mut self) -> Result<(), EngineDead> {
        self.shutdown_inner()
    }

    fn shutdown_inner(&mut self) -> Result<(), EngineDead> {
        if self.shutdown_signaled.swap(true, Ordering::SeqCst) {
            return Ok(());
        }
        // Best-effort: tell the worker to stop, then wait for the thread.
        self.global_stop.store(true, Ordering::SeqCst);
        let _ = self.cmd_tx.send(Command::Shutdown);
        if let Some(join) = self.join.take() {
            let _ = join.join();
        }
        Ok(())
    }
}

impl Drop for EngineHandle {
    fn drop(&mut self) {
        let _ = self.shutdown_inner();
    }
}

/// Sentinel returned from [`EngineHandle::send`] when the engine thread is
/// no longer running.
#[derive(Debug, Eq, PartialEq)]
pub struct EngineDead;

impl std::fmt::Display for EngineDead {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("engine worker thread is no longer running")
    }
}

impl std::error::Error for EngineDead {}

/// Commands sent from the UI to the engine.
#[derive(Clone, Debug)]
pub enum Command {
    /// Replace the engine's notion of the current position.
    SetPosition {
        /// Fully-specified position to start from.
        position: Position,
        /// History (in move order) since the start position. Used by the
        /// repetition detector.
        history: Vec<Move>,
    },

    /// Begin a search with the given config. The engine will emit
    /// [`Event::SearchProgress`] events periodically and exactly one
    /// [`Event::SearchComplete`] or [`Event::SearchAborted`] event at the
    /// end.
    StartSearch {
        /// Per-search configuration.
        config: EngineConfig,
    },

    /// Stop any in-flight search as soon as possible. Search will emit
    /// [`Event::SearchAborted`] (or [`Event::SearchComplete`] if it had
    /// already finished).
    Stop,

    /// Update non-search-time configuration (e.g., max threads).
    /// Takes effect at the next search.
    SetConfig {
        /// Soft cap on worker threads (1 = main thread only).
        max_threads: u8,
        /// Reserved for tracing-level toggling at runtime; v1 ignores this
        /// (logging level is set at process start via `RUST_LOG`).
        debug_logging: bool,
    },

    /// Shut down the engine thread cleanly.
    Shutdown,
}

/// Events emitted by the engine to the UI.
#[derive(Clone, Debug)]
pub enum Event {
    /// Periodic in-progress info (depth, eval, PV, nodes, nps). Emitted at
    /// most every ~100 ms during search to avoid UI flooding.
    SearchProgress(SearchInfo),

    /// Final result with the chosen move.
    SearchComplete(SearchResult),

    /// User-initiated abort. No `SearchInfo` is included because the
    /// search may not have produced a depth-1 result yet.
    SearchAborted,

    /// Non-fatal warning, e.g., "AVX2 not detected, using SSE2 fallback"
    /// or "ignored Stop while idle".
    Warning(String),

    /// Engine thread is exiting (after [`Command::Shutdown`]).
    Stopped,
}
