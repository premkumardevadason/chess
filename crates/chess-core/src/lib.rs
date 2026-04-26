//! Pure-logic chess core.
//!
//! This crate has zero dependencies on the engine or the UI. It owns:
//!
//! - [`Position`]: bitboard chess position with FEN parse / serialize and
//!   Zobrist hash.
//! - [`Move`] / [`MoveRecord`]: 16-bit packed move and undo record.
//! - [`legal_moves`]: the canonical move generator (classical bitboards in
//!   v1.0; magic-bitboards refactor deferred to the Carp port).
//! - [`Game`]: state machine with history, undo / redo, repetition table.
//! - [`GameResult`], [`DrawReason`]: terminal-state classification.
//! - SAN render (`san_for`) and SAN parse (`parse_san`).
//!
//! Everything else in the workspace (the engine, the UI) consumes this crate.

#![forbid(unsafe_code)]
#![warn(missing_docs)]

pub mod game;
pub mod movegen;
pub mod moves;
pub mod position;
pub mod rules;
pub mod san;

pub use game::{DrawReason, Game, GameMode, GameResult};
pub use movegen::{is_in_check, legal_moves};
pub use moves::{Move, MoveFlag, MoveList, MoveRecord, Promotion};
pub use position::{
    Bitboard, CastlingRights, Color, FenError, Piece, PieceType, Position, Square, STARTPOS_FEN,
};
pub use san::{parse_san, san_for};

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
