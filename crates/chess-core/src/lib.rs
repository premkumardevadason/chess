//! Pure-logic chess core.
//!
//! This crate has zero dependencies on the engine or the UI. It owns:
//!
//! - `Position`: bitboard chess position with FEN parse / serialize and Zobrist hash
//! - `Move` / `MoveRecord`: 16-bit packed move and undo record
//! - `legal_moves(&Position)`: the canonical move generator (magic bitboards)
//! - `Game`: state machine with history, undo / redo, repetition table
//! - `GameResult`, `DrawReason`: terminal-state classification
//! - SAN render and parse helpers
//!
//! Everything else in the workspace (the engine, the UI) consumes this crate.

#![forbid(unsafe_code)]
#![warn(missing_docs)]

/// Returns the package version string baked in at compile time.
pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_non_empty() {
        assert!(!version().is_empty());
    }
}
