//! `chess-app` library surface used by integration tests.
//!
//! `chess-app` ships as a single Windows binary (`chess-ai`), but the
//! UI / engine-link / settings layers also need to be reachable from
//! integration tests under `tests/`. We expose them here so
//! `tests/ui_smoke.rs` (T052) and the upcoming a11y / settings
//! integration tests (T077, T080) can drive them without launching
//! eframe.
//!
//! `main.rs` keeps its own `mod` declarations so the binary remains
//! self-contained; the library target compiles the same source files
//! a second time. The duplication is intentional — see
//! [contracts/cli-flags.md §Tests](../../specs/001-chess-ai-rewrite/contracts/cli-flags.md).

pub mod cli;
pub mod engine_link;
pub mod settings;
pub mod ui;
