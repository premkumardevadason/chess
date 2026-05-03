# chess-ai

A single-executable native Windows chess application with an integrated NNUE engine. Pure Rust, no installer, no network access.

This is the **v1 rewrite** described in [`specs/001-chess-ai-rewrite/`](specs/001-chess-ai-rewrite/). The legacy Java/Spring Boot project is preserved under [`legacy/`](legacy/) for reference but is not part of the v1 build.

## Highlights

- **Single `chess-ai.exe`** — statically-linked, no DLL or runtime dependencies.
- **Strong play out of the box** — Carp 3.0.1 alpha-beta + iterative deepening + transposition table + Lazy SMP, with an embedded NNUE network (`bins/net.bin`).
- **Fully offline** — the engine performs zero file or socket I/O during search (verified by ``cargo test --features no-io-audit``, see [contracts §3.5](specs/001-chess-ai-rewrite/contracts/engine-api.md)).
- **Reproducible mode** — given a fixed seed, the engine returns bit-identical PVs across runs.
- **Native UI** — `eframe`/`egui` board with drag-and-drop moves, legal-move highlighting, undo/redo, hints, and a "Swap Sides" action.
- **Comprehensive tests** — perft suite (22 positions), tactics, hint-mate, cancellation latency, time accuracy, AI-vs-AI determinism, 300-ply long-game stability, and a no-I/O source audit.

## System requirements

- Windows 10/11, x86-64
- ~50 MB disk for the executable + NNUE network
- ~200 MB resident RAM during search
- No network connection required at runtime

## Build and run

End-to-end build, test, and packaging instructions live in [`specs/001-chess-ai-rewrite/quickstart.md`](specs/001-chess-ai-rewrite/quickstart.md). The short version:

```powershell
# Build the release executable (static CRT)
cargo build --release --target x86_64-pc-windows-msvc

# Run the UI
target\x86_64-pc-windows-msvc\release\chess-ai.exe

# Run the full test suite
cargo test --workspace

# Run benchmarks (criterion)
cargo bench -p chess-core   --bench movegen
cargo bench -p chess-engine --bench nnue
cargo bench -p chess-engine --bench search
```

## Workspace layout

| Crate | Purpose |
|-------|---------|
| `crates/chess-core` | Pure-logic board, FIDE rules, move generation, SAN, game state machine |
| `crates/chess-engine` | Public engine API (channel-based), wrapping the vendored Carp 3.0.1 search core |
| `crates/chess-engine-chess` | Vendored Carp board / NNUE / magic-bitboard movegen |
| `crates/chess-engine-carp` | Vendored Carp search (alpha-beta + Lazy SMP + TT) |
| `crates/chess-app` | `eframe`/`egui` desktop UI (`chess-ai.exe`) |

## License

GPL-3.0-only. The combined work links the vendored Carp 3.0.1 search core (also GPL-3.0). See [`LICENSE`](LICENSE) and [`crates/chess-engine/UPSTREAM-LICENSE`](crates/chess-engine/UPSTREAM-LICENSE) for full text and upstream copyright notices.

The legacy Java project under `legacy/` retains its own license terms (see `legacy/`).