//! Engine-side configuration types — `EngineConfig`, `Mode`, `TimeControl`,
//! `StrengthPreset`, plus the `SearchInfo` / `SearchResult` reporting types
//! and the `Eval` numeric type. Implements task T020 of the CP-C plan.
//!
//! These types form the public configuration surface of the engine and are
//! re-exported at the crate root.

use std::time::Duration;

use chess_core::Move;
use serde::{Deserialize, Serialize};

/// Centipawn score from White's perspective. Positive values favour White,
/// negative favour Black, mate scores use `i32::MAX - depth`.
pub type Eval = i32;

/// Search-time mode flag.
#[derive(Copy, Clone, Eq, PartialEq, Debug, Default)]
pub enum Mode {
    /// Default: search using all available worker threads, may be
    /// non-deterministic across runs because of LazySMP scheduling.
    #[default]
    Normal,
    /// Single-thread, deterministic search seeded with `seed`. Two calls
    /// with the same `(seed, position, time_control)` produce bit-for-bit
    /// identical [`SearchResult`] and PV. See [contracts/engine-api.md §3.4].
    ///
    /// [contracts/engine-api.md §3.4]:
    /// ../../specs/001-chess-ai-rewrite/contracts/engine-api.md
    Reproducible {
        /// 64-bit deterministic seed.
        seed: u64,
    },
}

/// How long the engine should think before returning a move.
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum TimeControl {
    /// Search at most `Duration` of wall-clock time per move (with a 200 ms
    /// engine-side cushion to satisfy SC-009 / FR-007).
    PerMove(Duration),
    /// Search to a fixed depth. Used for testing and reproducibility.
    FixedDepth(u8),
    /// Search a fixed number of nodes. Used for benchmarking.
    FixedNodes(u64),
    /// Run forever — used for analysis / hint mode. The UI MUST send `Stop`
    /// to terminate.
    Infinite,
}

impl Default for TimeControl {
    fn default() -> Self {
        TimeControl::PerMove(Duration::from_secs(2))
    }
}

/// User-facing playing strength selector. Mapped to internal search-depth /
/// time / move-randomisation parameters by the engine. Concrete tuning
/// happens in CP-F (US2). The four variants match the on-disk schema
/// in [contracts/settings-file.md §2](../../specs/001-chess-ai-rewrite/contracts/settings-file.md)
/// and the row in [data-model.md §6](../../specs/001-chess-ai-rewrite/data-model.md).
#[derive(Copy, Clone, Eq, PartialEq, Debug, Default, Serialize, Deserialize)]
pub enum StrengthPreset {
    /// Beginner-level play (~800 Elo target).
    Beginner,
    /// Intermediate club player (~1400 Elo target).
    Intermediate,
    /// Advanced club player (~1800 Elo target).
    Advanced,
    /// Engine plays at full strength (no Elo cap).
    #[default]
    Maximum,
}

/// All the per-search settings the UI provides on `Command::StartSearch`.
#[derive(Clone, Debug)]
pub struct EngineConfig {
    /// Mode flag (Normal vs Reproducible).
    pub mode: Mode,
    /// How long to search.
    pub time_control: TimeControl,
    /// Target playing strength.
    pub strength: StrengthPreset,
    /// Soft cap on worker threads (1 = main thread only).
    pub max_threads: u8,
    /// Transposition-table size in MiB.
    pub tt_size_mib: usize,
    /// `true` if this search is for analysis/hints only (do not apply the
    /// resulting move to the game). Used by the UI to disable auto-play
    /// on `SearchComplete` per [T079].
    pub analysis_only: bool,
}

impl Default for EngineConfig {
    fn default() -> Self {
        Self {
            mode: Mode::default(),
            time_control: TimeControl::default(),
            strength: StrengthPreset::default(),
            max_threads: 1,
            tt_size_mib: 16,
            analysis_only: false,
        }
    }
}

/// Periodic in-progress search info emitted as `Event::SearchProgress`.
/// The UI uses this to drive eval bars, depth indicators, and PV display.
#[derive(Clone, Debug)]
pub struct SearchInfo {
    /// Fully completed iterative-deepening depth.
    pub depth: u8,
    /// Maximum ply reached (incl. extensions).
    pub seldepth: u8,
    /// Score relative to the side to move (centipawns; positive = better
    /// for the side to move). `Some(plies)` when forced mate is detected.
    pub score: Score,
    /// Principal variation as `chess-core` moves.
    pub pv: Vec<Move>,
    /// Total nodes searched so far.
    pub nodes: u64,
    /// Nodes per second (recent window).
    pub nps: u64,
    /// Time elapsed since `StartSearch`.
    pub elapsed: Duration,
}

/// Numeric or mate score for the side to move.
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub enum Score {
    /// Centipawn evaluation.
    Cp(Eval),
    /// Forced mate in `n` plies (positive = side-to-move mates, negative =
    /// side-to-move gets mated).
    Mate(i16),
}

/// Final search result emitted as `Event::SearchComplete`.
#[derive(Clone, Debug)]
pub struct SearchResult {
    /// Best move found, expressed in `chess-core` form. Guaranteed legal
    /// in the position passed to the most recent `Command::SetPosition`.
    pub mv: Move,
    /// Snapshot of the last `SearchInfo` (deepest completed iteration).
    pub info: SearchInfo,
    /// `true` if the search ran to its natural completion, `false` if it
    /// was cut short by `Command::Stop` but still produced a usable
    /// best move.
    pub completed: bool,
}
