//! Chess engine — forked Carp 3.0.1, refactored into a library.
//!
//! The engine runs on a dedicated worker thread and communicates with the UI
//! over `crossbeam-channel` channels. The full public API is defined in
//! `specs/001-chess-ai-rewrite/contracts/engine-api.md` and will be filled in
//! during T019–T021.
//!
//! For now this is a placeholder so the workspace can build cleanly.

#![warn(missing_docs)]

/// Placeholder — to be replaced by `Engine::new()` in T019.
pub fn placeholder() -> &'static str {
    "chess-engine placeholder"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn placeholder_works() {
        assert_eq!(placeholder(), "chess-engine placeholder");
    }
}
