# Quickstart: Chess AI Rewrite

**Feature**: `001-chess-ai-rewrite` · **Audience**: Developers building or running this project · **Date**: 2026-04-26

This document is the developer's hands-on entry point. Follow it from a clean Windows checkout to: (a) build the binary, (b) run the test suite, (c) launch the GUI, (d) verify the engine is at the strength target.

---

## 0. Prerequisites

| Tool | Version | Install |
|---|---|---|
| Windows 10/11 x86-64 | — | — |
| Rust toolchain | 1.83+ stable, MSVC ABI | `rustup install stable-x86_64-pc-windows-msvc` then `rustup default stable-msvc` |
| Visual Studio Build Tools | 2022, "Desktop development with C++" workload | https://visualstudio.microsoft.com/downloads/ |
| Git | any recent | https://git-scm.com/download/win |

Verify after install:

```powershell
rustc --version       # should print: rustc 1.83.0 (or newer) (x86_64-pc-windows-msvc)
cargo --version
git --version
```

No other dependencies are required. Specifically, you do **NOT** need:

- Java / JDK
- Node.js / npm
- Python
- Docker
- WSL
- A network connection at runtime

(The legacy Java application used all of these; the new Rust application uses none.)

---

## 1. Clone and orient

```powershell
git clone <repo-url> chess
cd chess
git checkout 001-chess-ai-rewrite
```

Directory map after clone:

```
chess/
├── Cargo.toml                  # workspace manifest
├── crates/
│   ├── chess-core/
│   ├── chess-engine/
│   │   └── nets/default.bin    # NNUE network (~30 MB)
│   └── chess-app/
├── tests/                      # integration tests
├── benches/                    # criterion benchmarks
├── specs/001-chess-ai-rewrite/ # this feature's spec, plan, research, contracts
├── docs/                       # (legacy reference docs — read-only)
└── legacy/                     # (legacy Java + TypeScript code — not built)
```

The `legacy/` and `docs/` folders are **not** part of the Cargo workspace and are ignored by `cargo build` / `cargo test`.

---

## 2. Build

### Debug build (fast compile, slow runtime)

```powershell
cargo build
```

First-run build time: ~3–5 minutes (compiles the engine fork + egui + wgpu + ~150 transitive crates). Subsequent incremental builds: ~10–30 seconds.

### Release build (slower compile, full strength)

```powershell
cargo build --release
```

Release build time: ~6–10 minutes the first time. The release `.exe` is at `target\release\chess-ai.exe`. **Always use the release build for engine strength testing** — debug builds run roughly 30× slower and will fail strength benchmarks.

### Static-CRT build (the shippable artifact)

```powershell
$env:RUSTFLAGS = "-C target-feature=+crt-static"
cargo build --release --target x86_64-pc-windows-msvc
$env:RUSTFLAGS = ""
```

Output: `target\x86_64-pc-windows-msvc\release\chess-ai.exe` — a single statically-linked `.exe` with no MSVC redistributable dependency. This is the artifact uploaded to GitHub Releases.

Verify zero external DLL dependencies:

```powershell
dumpbin /dependents target\x86_64-pc-windows-msvc\release\chess-ai.exe
```

Expected output: only `KERNEL32.dll`, `USER32.dll`, `GDI32.dll`, and similar core OS libraries. No `VCRUNTIME140.dll`, no `MSVCP140.dll`.

---

## 3. Run

```powershell
cargo run --release
```

A single window opens with a chess board. Click a piece to highlight legal moves, click a target to play. The engine responds with its move within ~5 seconds (default time control).

CLI flags:

```powershell
cargo run --release -- --version
cargo run --release -- --self-test
cargo run --release -- --portable
```

See `specs/001-chess-ai-rewrite/contracts/cli-flags.md` for the full flag list.

---

## 4. Test

### Unit tests (per-crate)

```powershell
cargo test --release
```

Runs all unit tests across all three crates. Expected runtime: ~30 seconds in release mode.

### Integration tests

```powershell
cargo test --release --test perft           # ~30s — move generation correctness
cargo test --release --test rules           # ~5s  — FIDE rules suite (1000+ positions)
cargo test --release --test tactics         # ~5min — Bratko-Kopec + Win-at-Chess
cargo test --release --test reproducible    # ~30s — Reproducible Mode determinism
cargo test --release --test cancel_latency  # ~5s  — Stop-command latency
cargo test --release --test settings_file   # ~1s  — TOML round-trip + corruption recovery
cargo test --release --test cli             # ~10s — CLI behaviour
cargo test --release --test ui_smoke        # ~5s  — egui headless harness
```

Or run all integration tests at once:

```powershell
cargo test --release --tests
```

Expected total: ~7 minutes. Every test must pass before a commit on `main` is allowed.

### Benchmarks

```powershell
cargo bench
```

Expected steady-state (Ryzen 5 5600 / 8 GB / Windows 11):

| Benchmark | Target |
|---|---|
| `movegen_initial_perft6` | ≥ 100M nps |
| `nnue_eval_throughput` | ≥ 5M evals/sec/thread |
| `search_kiwipete_depth12_8threads` | ≥ 1.5M nps |

Slower numbers are acceptable on weaker hardware; the targets are the SC-001 / SC-004 baseline.

---

## 5. Strength verification (≥ 3400 Elo gate)

Two ways to estimate strength locally:

### 5.1 Pin against a known reference (cutechess-cli)

If you already have `cutechess-cli` and a reference engine of known Elo (e.g., `Stockfish 11` rated ~3300 Elo) installed:

```powershell
cutechess-cli `
  -engine cmd=target\release\chess-ai.exe arg=--uci-bridge `
  -engine cmd=stockfish-11.exe `
  -each tc=10+0.1 `
  -games 200 -concurrency 4 `
  -openings file=openings\8moves_v3.pgn format=pgn `
  -pgnout result.pgn
```

(Note: v1 of `chess-ai.exe` does NOT speak UCI; the `--uci-bridge` flag is a v2 feature. For v1, strength is verified via the bench-based CCRL-equivalent below.)

### 5.2 Bench-based estimate (v1 method)

Run the built-in self-test plus the tactics suite and compare against published Carp 3.x scores:

```powershell
cargo run --release -- --self-test
cargo test --release --test tactics -- --nocapture
```

`tactics` prints the percentage solved on Bratko-Kopec at 5 s/position. Carp 3.0.1 scores ≥ 23/24 on Bratko-Kopec at this time control. **Our fork must score ≥ 22/24** to be considered at-strength (a 1-position regression budget).

---

## 6. Format and lint

```powershell
cargo fmt --all
cargo clippy --all-targets -- -D warnings
```

Both must be clean before a commit. CI enforces this.

---

## 7. Run the engine in Reproducible Mode

For test reproduction or regression analysis:

1. Open the app.
2. `Settings` → check `Reproducible Mode (default)`. Or press `Ctrl+R` once on the Game screen.
3. The top-right badge changes to "Reproducible".
4. Play a game. The engine will use single-thread + fixed seed.
5. Replay the same opening sequence in a fresh session — every engine move must be identical.

---

## 8. Common issues

| Symptom | Cause | Fix |
|---|---|---|
| `error: linker 'link.exe' not found` | VS Build Tools missing | Install "Desktop development with C++" workload |
| Build error mentioning `VCRUNTIME140.dll` at runtime | non-static-CRT build run on a machine without VC++ Redist | Use the static-CRT build (§2) |
| Engine very slow on first run | running the debug build | Always use `--release` for strength tests |
| `cargo run` opens then closes immediately | NNUE network blob corrupted | Run `cargo clean -p chess-engine` then rebuild |
| Settings not saving | `%APPDATA%\chess-ai\` not writable | Run with `--portable` to use exe-dir |

---

## 9. What's intentionally NOT in this build

To match the spec and Constitution, the following are **explicitly excluded** and will be rejected if added back:

- Save / Load games (Q3) — even via clipboard or PGN export
- Online play, Lichess sync, opening explorer, cloud sync (FR-021)
- A second AI engine, an "AI selector", or any pluggable engine system (Constitution Principle II)
- Telemetry, crash reporters, or auto-update checks (FR-021, R-14)
- Any browser, webview, or HTML-rendering library (Constitution Principle III)
- A separate engine binary, UCI bridge, or DLL plugin (Constitution Principle II / FR-005)
- Game files of any format (Q3, FR-013 removed)

If a code change introduces any of the above, it is out of scope for v1 and the change must be deferred to v2 with explicit Constitution approval.

---

## 10. Where to go next

- **Architecture deep-dive**: `specs/001-chess-ai-rewrite/plan.md`
- **Why these technologies**: `specs/001-chess-ai-rewrite/research.md`
- **Data structures**: `specs/001-chess-ai-rewrite/data-model.md`
- **API and file contracts**: `specs/001-chess-ai-rewrite/contracts/`
- **Task list (after `/speckit.tasks`)**: `specs/001-chess-ai-rewrite/tasks.md`
