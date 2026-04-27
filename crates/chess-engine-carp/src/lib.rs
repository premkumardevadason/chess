/// Carp engine lib (vendored, UCI binary stripped).
/// Exposes search/position/threadpool functionality used by the chess-engine wrapper crate.
pub mod bench;
pub mod clock;
pub mod move_picker;
pub mod position;
pub mod search;
pub mod search_params;
pub mod search_tables;
pub mod syzygy;
pub mod thread;
pub mod tt;
