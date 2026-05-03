//! T096 — No-I/O audit (FR-021 / FR-022, SC-007, contracts/engine-api.md §3.5).
//!
//! The engine MUST NOT open any file, socket, or other system resource
//! during `StartSearch`. The NNUE network is loaded once at
//! `Engine::new` (embedded in the binary) and the search is pure
//! compute thereafter.
//!
//! True dynamic interposition of `std::fs` / `std::net` is not
//! achievable in stable Rust without `LD_PRELOAD`-style hooks. We
//! therefore implement the contract as a *source-level* audit: under
//! the `no-io-audit` feature, this test walks every `.rs` file in the
//! engine crate and fails if it references any banned API. This makes
//! every forbidden call site fail the build for the audit profile —
//! the moral equivalent of replacing `std::fs` and `std::net` with
//! panicking shims. Run with:
//!
//! ```text
//! cargo test -p chess-engine --features no-io-audit --test no_io
//! ```
//!
//! For the default test profile we still execute a smoke search and
//! assert it returns a result, mirroring the runtime side of the
//! contract: a successful search occurred without I/O calls (the
//! audit guarantees the latter at the source level).

use std::path::{Path, PathBuf};
use std::time::Duration;

use chess_core::{Position, STARTPOS_FEN};
use chess_engine::{Command, Engine, EngineConfig, Event, TimeControl};

/// Banned symbols that imply file or socket I/O. Comments in the
/// engine source are stripped before matching, so documentation may
/// freely mention these names.
const BANNED: &[&str] = &[
    "std::fs",
    "std::net",
    "std::os::unix::net",
    "std::os::windows::io",
    "tokio::fs",
    "tokio::net",
    "async_std::fs",
    "async_std::net",
    "File::open",
    "File::create",
    "OpenOptions",
    "TcpStream",
    "TcpListener",
    "UdpSocket",
    "UnixStream",
    "UnixListener",
    "fs::read",
    "fs::write",
    "fs::File",
];

fn engine_src_root() -> PathBuf {
    // CARGO_MANIFEST_DIR points at crates/chess-engine.
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("src")
}

fn collect_rs_files(root: &Path, out: &mut Vec<PathBuf>) {
    let entries = match std::fs::read_dir(root) {
        Ok(e) => e,
        Err(_) => return,
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect_rs_files(&path, out);
        } else if path.extension().and_then(|s| s.to_str()) == Some("rs") {
            out.push(path);
        }
    }
}

/// Strip `//` line comments and `/* ... */` block comments so that the
/// audit ignores documentation references to banned symbols.
fn strip_comments(src: &str) -> String {
    let bytes = src.as_bytes();
    let mut out = String::with_capacity(src.len());
    let mut i = 0;
    while i < bytes.len() {
        let b = bytes[i];
        if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'/' {
            // Line comment — skip to newline.
            while i < bytes.len() && bytes[i] != b'\n' {
                i += 1;
            }
        } else if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'*' {
            // Block comment — skip until matching `*/` (no nesting
            // support; sufficient for the engine source).
            i += 2;
            while i + 1 < bytes.len() && !(bytes[i] == b'*' && bytes[i + 1] == b'/') {
                i += 1;
            }
            i = i.saturating_add(2);
        } else {
            out.push(b as char);
            i += 1;
        }
    }
    out
}

#[cfg(feature = "no-io-audit")]
#[test]
fn engine_source_does_not_reference_io_apis() {
    let root = engine_src_root();
    let mut files = Vec::new();
    collect_rs_files(&root, &mut files);
    assert!(!files.is_empty(), "no engine source files found at {root:?}");

    let mut violations: Vec<String> = Vec::new();
    for file in &files {
        let src = std::fs::read_to_string(file)
            .unwrap_or_else(|e| panic!("read {file:?}: {e}"));
        let stripped = strip_comments(&src);
        for needle in BANNED {
            if stripped.contains(needle) {
                violations.push(format!("{}: references `{needle}`", file.display()));
            }
        }
    }

    assert!(
        violations.is_empty(),
        "no-I/O audit failed — engine source uses banned APIs:\n{}",
        violations.join("\n"),
    );
}

/// Runtime smoke check: a fixed-depth search completes without panic.
/// The static audit (above, gated behind `no-io-audit`) guarantees the
/// "no file/socket opened" half of the contract; this test covers the
/// "search still works" half so accidental regressions in either
/// direction are caught.
#[test]
fn fixed_depth_search_completes_without_panic() {
    let engine = Engine::new();
    let handle = engine.spawn();
    let pos = Position::from_fen(STARTPOS_FEN).unwrap();
    handle
        .send(Command::SetPosition {
            position: pos,
            history: vec![],
        })
        .unwrap();
    handle
        .send(Command::StartSearch {
            config: EngineConfig {
                time_control: TimeControl::FixedDepth(4),
                max_threads: 1,
                ..EngineConfig::default()
            },
        })
        .unwrap();

    let deadline = std::time::Instant::now() + Duration::from_secs(5);
    let mut got_result = false;
    while std::time::Instant::now() < deadline {
        if let Some(ev) = handle.try_recv() {
            if matches!(ev, Event::SearchComplete(_)) {
                got_result = true;
                break;
            }
        } else {
            std::thread::sleep(Duration::from_millis(5));
        }
    }
    assert!(got_result, "engine did not produce SearchComplete in 5s");
}
