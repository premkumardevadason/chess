//! Chess engine — wrapper crate around the vendored Carp 3.0.1 search core.
//!
//! ## Layout
//!
//! - [`adapter`] (T017): translates between `chess-core` types and Carp's
//!   internal types at the API boundary. Position conversion via FEN
//!   round-trip; move conversion via direct (src, tgt, type) translation
//!   (Carp ↔ chess-core square indices differ by an XOR with 56).
//! - [`config`] (T020): public configuration types — `EngineConfig`,
//!   `Mode`, `TimeControl`, `StrengthPreset`, `SearchInfo`, `SearchResult`,
//!   `Eval`, `Score`.
//! - [`api`] (T019): public API surface — `Engine`, `EngineHandle`,
//!   `Command`, `Event`, `EngineDead`. Channel-based controller backed by
//!   `crossbeam-channel`.
//! - [`worker`] (T021): worker-thread loop. Holds Carp's `ThreadPool`
//!   plus a shared `Arc<AtomicBool>` stop flag so `Command::Stop` can
//!   abort an in-flight search within ≤ 50 ms (per contracts §3.7).
//!
//! ## What ships in CP-C
//!
//! The wrapper goes *just* far enough to satisfy contracts:
//! `SetPosition` → `StartSearch` → one `SearchComplete` (or
//! `SearchAborted`) event with a legal move. Periodic `SearchProgress`
//! events and reproducible-mode determinism are stubbed and will be
//! filled in by CP-D / CP-J (T097a).

#![warn(missing_docs)]

pub mod adapter;
pub mod api;
pub mod config;
mod worker;

pub use adapter::AdapterError;
pub use api::{Command, Engine, EngineDead, EngineHandle, Event};
pub use config::{
    EngineConfig, Eval, Mode, Score, SearchInfo, SearchResult, StrengthPreset, TimeControl,
};

pub use chess_core::{Move, MoveRecord, Position};

/// Returns the package version string baked in at compile time.
pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

#[cfg(test)]
mod tests {
    use super::*;

    /// T018 smoke test — the embedded NNUE network loads, evaluates a
    /// position, and returns a finite centipawn value.
    ///
    /// This does NOT exercise the search; it exists to catch silent
    /// breakage of the `include_bytes!("../../bins/net.bin")` path in the
    /// vendored chess-engine-chess crate.
    #[test]
    fn nnue_network_embedded_and_evaluates() {
        use chess::{board::Board, nnue::NNUEState, piece::Color};

        let board = Board::default();
        let state = NNUEState::from_board(&board);
        let eval = state.evaluate(Color::White);

        // The startpos eval should be near 0 (NNUE returns scaled cp);
        // bound it loosely to catch byte-order or path errors.
        assert!(
            eval.abs() < 5_000,
            "NNUE startpos eval out of plausible range: {eval}"
        );
    }

    /// T018 smoke test — magic-bitboard tables (sliders.bin, etc.) load
    /// and produce sensible legal-move counts.
    #[test]
    fn carp_movegen_sane_at_startpos() {
        use chess::board::{Board, QUIETS};

        let board = Board::default();
        let moves = board.gen_moves::<QUIETS>();
        assert_eq!(moves.len(), 20, "Carp must report 20 startpos moves");
    }

    /// T019 / T020 surface check — the public types compile and can be
    /// used to build a default config + spawn an engine + immediately
    /// shut it down.
    #[test]
    fn engine_handle_round_trip() {
        let engine = Engine::new();
        let handle = engine.spawn();
        // Sanity: send something legal and then shut down.
        let _ = handle.send(Command::Shutdown);
        // Drain any final events (Stopped) so the test's drop doesn't
        // block while waiting for them.
        std::thread::sleep(std::time::Duration::from_millis(50));
        let evs = handle.drain();
        assert!(
            evs.iter().any(|e| matches!(e, Event::Stopped)),
            "expected Event::Stopped after Shutdown, got {:?}",
            evs
        );
    }
}
