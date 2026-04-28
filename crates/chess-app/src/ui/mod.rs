//! UI subsystem for `chess-app`.
//!
//! Modules:
//!
//! - [`theme`] — palette + procedural piece drawing (T033, T034).
//! - [`board`] — the 8×8 board widget with click-click and drag-and-drop
//!   move input plus highlights and check glow (T035–T037, T039, T040).
//! - [`promotion`] — modal asking the user to pick Q/R/B/N when a pawn
//!   reaches the back rank (T038).
//! - [`game_screen`] — top-level Game screen layout: board on the left,
//!   side panel with move list, captured-piece tray, status, and
//!   buttons on the right (T041, T041a, T042–T046).
//! - [`settings_screen`] — section-organised settings form (T053–T060).

pub mod board;
pub mod game_screen;
pub mod promotion;
pub mod settings_screen;
pub mod theme;

pub use game_screen::GameScreen;
pub use settings_screen::{SettingsOutcome, SettingsScreen};
