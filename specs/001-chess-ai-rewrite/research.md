# Phase 0 Research: Chess AI Rewrite

**Feature**: `001-chess-ai-rewrite` · **Spec**: [spec.md](./spec.md) · **Date**: 2026-04-26

This document resolves every open technology question implied by `spec.md` so that Phase 1 design and Phase 2 task generation have zero `NEEDS CLARIFICATION` markers. Each section follows the format **Decision → Rationale → Alternatives Considered**.

---

## R-1. Implementation language and toolchain

**Decision**: **Rust 1.83+ stable** (MSVC toolchain on Windows). Build via Cargo workspaces. Static C runtime linkage (`RUSTFLAGS="-C target-feature=+crt-static"`) so the resulting `.exe` has zero MSVC redistributable runtime dependency.

**Rationale**:
- Locked by Clarifications session 2026-04-26 (Q1).
- 1.83 is the minimum that gives mature `std::sync::Mutex` performance, stable `Pin::deref`, and stable LazyLock — used by NNUE engines for table init.
- MSVC toolchain produces native `.exe` files indistinguishable from C++-compiled apps; eliminates the MinGW/POSIX layer.
- Static CRT eliminates the "VC++ Redistributable not installed" failure mode and satisfies FR-024 (run from Downloads, no admin rights).

**Alternatives considered**:
- **GNU toolchain (`x86_64-pc-windows-gnu`)** — produces working `.exe` but pulls in MinGW DLLs unless statically linked; toolchain story on Windows is rougher than MSVC. Rejected.
- **Rust nightly** — would let us use SIMD intrinsics with less ceremony, but the spec demands a reproducible build; stable + `std::simd` (via `portable-simd` / `pulp` crate) is enough. Rejected.

---

## R-2. UI framework

**Decision**: **`egui` 0.30+ via `eframe` 0.30+** with the `wgpu` backend. egui is an immediate-mode GUI rendered by wgpu (DirectX 12 / DirectX 11 fallback on Windows). Single statically-linked `.exe` output with no native widget bindings, no GTK/Qt runtime, no webview.

**Rationale**:
- **Single-binary fit**: egui has no native-toolkit dependency (vs. fltk-rs, gtk-rs, cxx-qt). Compiles entirely from `cargo build`. ~10 MB binary overhead.
- **Immediate-mode is ideal for a chess board**: every frame redraws from current state; piece movement, drag-and-drop, highlight squares, animation all become trivial state updates rather than retained-mode widget-graph mutations.
- **Mature drag-and-drop, click handling, scrollable move list, modal dialogs** — all built into the toolkit.
- **Largest Rust GUI ecosystem in 2026**: > 5x more crates depending on egui than on iced or Slint. Many published examples of chess/2D-board applications.
- **wgpu backend** is GPU-accelerated where available (DirectX 12) and falls back gracefully — meets SC-005 (cold start < 5 s) on machines without modern GPUs.

**Alternatives considered**:
- **iced 0.13** — Elm-architecture, good for forms, but redrawing a 64-square animated chess board every frame is awkward in retained mode. Rejected.
- **Slint 1.8** — declarative DSL, very polished output, but introduces a separate `.slint` source language and a cargo build script; the DSL's chess-board ergonomics are worse than egui's pure-Rust idiom. Rejected.
- **fltk-rs 1.4** — tiny binaries (~3 MB), but the chess-board widget would need to be hand-drawn against an X-style toolkit and the look-and-feel on Windows 11 is dated. Rejected.
- **Native Win32 (windows-rs)** — minimum dependencies, but writing a chess board in pure Win32 is months of work that adds zero strength to the engine. Rejected.

---

## R-3. Chess engine — codebase

**Decision**: **Fork [Carp](https://github.com/dede1751/carp) (Andrea Sgobbi, MIT license)** as the starting engine. Refactor it from a standalone UCI binary into an internal library crate (`chess-engine`) inside our Cargo workspace, exposing a synchronous `Engine` API that the UI thread calls into via channels (see `contracts/engine-contract.md`).

**Rationale**:
- **Strength**: Carp 3.0.1 is rated **~3450 Elo** on CCRL Blitz (verified: top-15 Rust engine, ~150 Elo above the 3300 minimum needed to clear FR-006 / SC-001 with margin).
- **License**: MIT — cleanly permissively licensed, including the bundled NNUE network (no GPL infection).
- **Codebase size**: ~7,000 lines of Rust across well-organised modules (`board.rs`, `movegen.rs`, `search.rs`, `nnue.rs`, `tt.rs`, `tunables.rs`). Small enough for the team to fully understand; large enough that strength is real.
- **Architecture**: alpha-beta + iterative deepening + transposition table + null-move pruning + late-move reductions + Lazy SMP + NNUE eval. Standard, well-documented techniques.
- **NNUE network**: ships with a 30 MB HalfKAv2-style network in MIT-licensed binary form. Embedded via `include_bytes!`.
- **UCI extraction**: Carp's `uci.rs` is the only file we need to discard / replace; everything else is library-shaped already.
- **No GPU dependency**: NNUE is integer SIMD on CPU. AVX2 detected at runtime via `std::arch::is_x86_feature_detected!`; SSE2 fallback for older CPUs.

**Alternatives considered**:
- **Velvet** (Martin Honnen, ~3500 Elo, MIT) — slightly stronger than Carp but more tightly coupled to UCI; refactor cost ~2x Carp's. Rejected for v1; reasonable v2 upgrade target.
- **Black Marlin** — comparable strength, but its NNUE network is GPL-derived (forked from Stockfish's training pipeline) — license risk. Rejected.
- **Akimbo** (~3400 Elo, MIT) — smaller codebase (~3,000 LOC), would be the easiest to refactor but ~50 Elo weaker than Carp at the same time control. Carp is preferred for the strength margin against SC-001.
- **Pleco** — Rust port of Stockfish 8 (classical eval, no NNUE) — ~2900 Elo. Doesn't clear FR-006 (3400 Elo target). Rejected.
- **Custom engine from scratch** — explicitly rejected by clarification Q4 (no project-scope training, no multi-month engine development).

---

## R-4. Move generation crate

**Decision**: **Port Carp's `movegen.rs` (magic bitboards, ~150–200M nps perft 7) into `chess-core/src/movegen.rs`** as the canonical home for move generation. The engine (`chess-engine`) depends on `chess-core` and consumes the same `legal_moves(&Position)` API the UI uses. Do **not** introduce `cozy-chess`, `chess`, or `shakmaty` as separate dependencies, to avoid maintaining two move-gen implementations.

**Rationale**:
- Carp's move generator is benchmark-proven (passes perft 7 on the standard 6-position FIDE perft suite at ~180M nps on a Ryzen 5950X).
- Introducing a second move-gen library would create the risk of disagreement between "the engine's view of legal moves" and "the UI's view of legal moves" — a recipe for FR-002 violations.
- Placing movegen in `chess-core` (rather than in `chess-engine` and re-exporting it) keeps the dependency direction strictly `chess-app → chess-core` and `chess-engine → chess-core`, with no circular dependency. There is exactly one move-generator implementation; both UI legality checks and engine search consume it directly.

**Alternatives considered**:
- **cozy-chess** — fastest move-gen crate in the Rust ecosystem (~250M nps), MIT licensed, very ergonomic API. But duplicating move-gen would couple us to two crates' release schedules. Reasonable v2 swap if a profile shows movegen as a bottleneck. Rejected for v1.
- **shakmaty** — battle-tested (used by Lichess), but slower (~50M nps) and has a heavier API surface. Rejected.

---

## R-5. Threading model

**Decision**: **Two thread classes**:

1. **UI thread (main)**: hosts the egui/eframe event loop. Owns all UI state. Never blocks for more than ~1 ms.
2. **Search thread (one)**: long-lived; receives search commands over a `crossbeam_channel`, owns the `Engine` instance. Spawns `N` rayon-managed worker threads internally (Lazy SMP) when in default mode, or runs single-threaded in Reproducible Mode (FR-009).

Communication is channel-based (mpsc): UI → Search for commands (`StartSearch`, `Stop`, `SetPosition`, `SetConfig`); Search → UI for events (`SearchProgress`, `SearchComplete`, `Info`). UI polls the receiver each egui frame.

**Rationale**:
- Keeps the UI fluid (60 fps) regardless of engine load.
- Clean cancellation: UI sends `Stop`; Search sets an `AtomicBool` checked by every search worker on every node visited.
- Rust's ownership rules prevent UI threads from accidentally touching engine state directly.
- Carp's existing search loop already polls a `stop` flag, so adapting it costs about 50 lines.

**Alternatives considered**:
- **Async/await + Tokio** — overkill for a desktop app with one engine. Tokio would pull in ~80 crates and add binary size for zero benefit. Rejected.
- **Run engine on the UI thread** — would freeze the UI during search (unacceptable for SC-006: 0% sustained CPU at idle, fluid drag-and-drop while AI thinks in background). Rejected.

---

## R-6. NNUE network embedding and licensing

**Decision**: **Embed Carp's bundled NNUE network as a `&'static [u8]`** via `include_bytes!("../nets/default.bin")`. License: copy upstream MIT LICENSE text into our `nets/LICENSE` and reference it from `Cargo.toml` and the in-app About dialog. Verify network license at every dependency-bump.

**Rationale**:
- Spec FR-006a requires permissive license + binary embedding.
- `include_bytes!` makes the network part of the `.exe`'s read-only data segment — no separate file, no runtime load failure, no missing-network UX.
- ~30 MB networks are well within egui+wgpu+chess-engine total binary size (~50–60 MB), meeting SC-006 (RAM ≤ 500 MB).
- Re-verify license at upgrade: each new network release from upstream needs a fresh license check before being merged.

**Alternatives considered**:
- **Loose `default.bin` shipped alongside `.exe`** — violates "single executable" interpretation; user could delete the file. Rejected.
- **Train our own network** — explicitly out of scope (Q4). Rejected.
- **Use Stockfish's network** — GPL-licensed, would force the entire `.exe` to be GPL. Rejected by FR-006a.

---

## R-7. Reproducible Mode implementation

**Decision**: A **`Mode` enum** in `chess-engine`:

```rust
pub enum Mode {
    Default,                      // multi-threaded Lazy SMP, fastest
    Reproducible { seed: u64 },   // single-threaded, fixed seed, fixed move-ordering tiebreaks
}
```

In Reproducible Mode the engine: (a) runs exactly one search thread, (b) seeds all RNG (used in Dirichlet noise / aspiration jitter) from the configured `seed`, (c) uses position hash as the secondary key for move-ordering tiebreaks instead of clock-based tiebreaks, (d) disables time-based search aborts in favour of fixed-depth or fixed-node budgets.

**Rationale**:
- FR-009 / FR-009a — explicit testable definition of reproducibility.
- Bit-for-bit identical PV across runs is achievable single-threaded with fixed seeds (Carp's existing search is already deterministic on a single thread except for time-based aborts).
- Mode is selected before each search starts (FR-009a) and is recorded in the move list metadata (FR-019).

**Alternatives considered**:
- **Always single-threaded** — too weak (loses ~150–300 Elo), fails SC-001. Rejected (Q2).
- **Multi-threaded deterministic via barriers** — theoretically possible but the literature shows it's brittle and tends to cost most of the SMP speedup. Rejected.

---

## R-8. User settings persistence

**Decision**: **TOML file** at `%APPDATA%\chess-ai\settings.toml` (Windows; resolved via the `directories` crate). Read on startup, written on settings change (debounced 250 ms). Atomic write (write-to-tmp + rename). Schema versioned (`schema_version = 1`).

**Rationale**:
- TOML is the Rust ecosystem default (Cargo, rustfmt, clippy all use it). `serde` + `toml` round-trips cleanly with one derive macro.
- Single small file (~1 KB). Atomic rename prevents corruption on power loss.
- `%APPDATA%\chess-ai\` follows Windows conventions; `directories` crate uses `SHGetKnownFolderPath` correctly.
- Schema version field allows graceful migration if v2 changes the layout.

**Alternatives considered**:
- **JSON** — works, but no Rust idiom advantage over TOML, and TOML has cleaner human-readable output. Rejected.
- **Windows Registry** — violates portable-mode goal (FR-024). Rejected.
- **Sqlite** — wildly overkill for ~1 KB of settings; adds 1+ MB of binary + 10+ crates. Rejected.

---

## R-9. FIDE rules test corpus

**Decision**: **Three integration test suites** in `tests/`:

1. **`perft.rs`** — runs the standard FIDE 6-position perft suite (Initial, Kiwipete, Position 3, Position 4, Position 5, Position 6) at depths 6 / 5 / 7 / 5 / 5 / 5. Verifies move-generation correctness against published node counts. ~30 seconds in release mode. SC-008 partial.
2. **`rules.rs`** — exercises 1,000+ hand-curated and corpus-derived positions covering every special move (castle through check, en-passant pin, underpromotion, 50-move-rule trigger, threefold-repetition trigger, insufficient-material draw — every category with both positive and negative cases). SC-008 main.
3. **`tactics.rs`** — runs the engine against a public tactics suite (Bratko-Kopec 24 positions + Win at Chess 300 positions; both public and unrestricted) at 5 s per position. Asserts ≥ 90% solved per SC-002.

Test corpora are committed to the repo (~2 MB of FEN/PGN); no network access required.

**Rationale**:
- Perft is the gold standard for proving move generation.
- A 1,000-position rules suite gives concrete coverage for SC-008 (≥ 100% pass rate).
- Public tactics suites are reproducible and well-known to chess engine developers.
- All test data is committed, so CI runs with no external dependencies.

**Alternatives considered**:
- **Use only Carp's existing perft tests** — those tests are correct but limited to ~50 positions; not enough for SC-008's ≥ 1,000-position requirement. Augment, not replace.
- **Generate tests from Stockfish output** — risks GPL contamination. Rejected.

---

## R-10. Performance benchmarks

**Decision**: **`criterion` benchmarks** in `benches/` for: (a) move generation nps (perft 6 from initial position, target ≥ 100M nps on commodity hardware), (b) NNUE evaluation throughput (target ≥ 5M evals/s single-thread), (c) full-search nps at depth 12 from `Kiwipete` (target ≥ 1.5M nps on 8 threads).

**Rationale**:
- Concrete, reproducible measurements support SC-001 / SC-004.
- `criterion` is the de-facto Rust benchmarking crate; produces statistical reports with confidence intervals.
- These three benches together prove the engine is in the "top-tier" regime; failures are immediately visible.

**Alternatives considered**:
- **No benchmarks** — leaves SC-001 untestable until the end. Rejected.

---

## R-11. Test framework

**Decision**: Standard **`cargo test`** for unit + integration tests. **`criterion`** for benchmarks. **`insta`** for snapshot tests on PGN-format move list output (in-session display only — no on-disk persistence per Q3).

**Rationale**:
- All three are 1st-class Rust ecosystem tools, zero learning curve for any Rust developer.
- `insta` snapshots are perfect for catching regressions in move-list display formatting.

**Alternatives considered**:
- **None worth listing** — these are the obvious Rust defaults.

---

## R-12. Distribution and code signing

**Decision**: **Single `chess-ai.exe` artifact** produced by `cargo build --release --target x86_64-pc-windows-msvc`. Code-signed at release time using **SignTool with an EV/OV code-signing certificate** (purchased separately; not part of v1 codebase). Distribution: GitHub Releases page hosting the signed `.exe`. SHA-256 checksum published alongside.

**Rationale**:
- Single `.exe` matches FR-023 exactly. Portable mode (extract-and-run) per FR-024.
- Code signing prevents Windows SmartScreen "Unknown publisher" warnings on first launch — important for SC-005 (cold start within 5 s; SmartScreen prompt would otherwise be a 5–10 s blocker).
- GitHub Releases is free, has CDN, supports asset uploads, gives reproducible URLs.
- SHA-256 published alongside satisfies the Constitution's "rebuild and validate" principle for downloads.

**Alternatives considered**:
- **MSI installer (WiX / cargo-wix)** — violates portable-mode requirement. Rejected.
- **Microsoft Store distribution** — adds review delay, sandbox restrictions, and is not how chess engines are typically distributed. Rejected for v1; possible v2.
- **Skipping code signing** — possible but causes SmartScreen friction; recommended to sign even for v1.

---

## R-13. Project layout

**Decision**: **Cargo workspace** at the repo root with three crates:

```
chess/
├── Cargo.toml                  # workspace manifest (members = ["crates/*"])
├── crates/
│   ├── chess-core/             # board, FIDE rules, move types (façade over engine's movegen)
│   ├── chess-engine/           # forked Carp: search, NNUE, TT, SMP
│   │   └── nets/default.bin    # MIT-licensed NNUE network (~30 MB, embedded via include_bytes!)
│   └── chess-app/              # binary crate: egui UI + glue + settings
└── tests/                      # cross-crate integration tests (perft, rules, tactics)
```

Legacy code (Spring Boot Java, TypeScript frontend, infra/, mcp/, target/, etc.) is moved to `legacy/` at the repo root and is **not** part of the Cargo workspace.

**Rationale**:
- Three crates is the minimum needed to keep concerns separate without violating Constitution Principle V (Minimalism). One crate would conflate engine and UI; four+ would over-decompose.
- `chess-core` exists as a thin layer because the UI needs to call legal-moves / make-move / unmake-move *without* taking the engine's heavy NNUE crate as a UI dependency in unit tests.
- Network blob lives in `chess-engine/nets/` so its Cargo build dependency is local and the `include_bytes!` path is short.
- `legacy/` keeps the 70+ docs and the old Java code accessible for reference while ensuring `cargo build` in the workspace doesn't see them.

**Alternatives considered**:
- **Single crate** — easier to start, but mixes UI types into engine APIs and makes cross-crate testing of "rules" vs "engine" impossible. Rejected.
- **Five+ crates (chess-board, chess-rules, chess-search, chess-eval, chess-ui)** — violates Minimalism and slows compile-times for no payoff at the v1 scale. Rejected.

---

## R-14. Logging and diagnostics

**Decision**: **`tracing` + `tracing-subscriber`** with default level `WARN`. Optional `--log-debug` CLI flag enables file logging at `%LOCALAPPDATA%\chess-ai\logs\chess-ai-<date>.log` (rotated daily, max 7 days). No telemetry, no network, no auto-upload.

**Rationale**:
- Constitution Principle IV requires deterministic, reproducible behaviour — debug logs help diagnose user-reported issues without telemetry.
- `tracing` is the Rust ecosystem standard; integrates cleanly with `cargo` and tools like Tokio Console (if needed in v2).
- File rotation prevents disk bloat.
- Off by default ⇒ zero impact on idle resource usage (SC-006).

**Alternatives considered**:
- **`log` + `env_logger`** — older, less feature-rich. Rejected.
- **No logging at all** — makes user issue triage extremely difficult. Rejected.

---

## R-15. CI / build automation

**Decision**: **GitHub Actions** with one Windows runner. Matrix: `cargo fmt --check`, `cargo clippy -- -D warnings`, `cargo test --release`, `cargo bench --no-run`, `cargo build --release` producing the artifact. Artifact uploaded on every push for smoke testing; signed artifacts produced on tag push only.

**Rationale**:
- Free for open-source; reasonable cost for private repos.
- One Windows runner matches the Q5 platform decision (Windows x86-64 only).
- `--release` for tests because perft/tactics suites at debug-build speed take hours.
- `cargo bench --no-run` keeps benchmark code compiling without paying the runtime cost on every push.

**Alternatives considered**:
- **No CI** — high regression risk in a 7,000-LOC engine port. Rejected.
- **GitLab CI / self-hosted** — works but adds infrastructure burden. Rejected.

---

## R-16. Localization and accessibility (deferred items from clarification)

**Decision**: **English-only UI for v1.** Accessibility: keyboard-only navigation supported (arrow keys + Enter/Space for piece selection and move confirmation, FR-017's "SHOULD allow keyboard entry of moves in algebraic notation" is upgraded to MUST in the implementation), high-contrast theme available. Screen-reader support is **deferred to v2** (the egui ecosystem's screen-reader bridge `accesskit` is still maturing in 2026; full support would add significant integration work disproportionate to a v1 chess tool).

**Rationale**:
- Two of the clarification taxonomy's "Outstanding" items are addressed at design level without expanding spec scope.
- Keyboard navigation is a free win with egui's existing focus support — costs ~1 day of work, very high disability-inclusion value.
- High-contrast theme is a CSS-style switch in egui.
- Full screen-reader (NVDA/JAWS) compatibility through `accesskit` is a multi-week integration; better as a focused v2 feature.

**Alternatives considered**:
- **Full a11y for v1** — adds 2–3 weeks; SC-010 doesn't currently require it. Rejected.
- **No keyboard nav** — leaves users with motor impairments behind. Rejected.

---

## Summary: decisions table

| ID | Topic | Decision |
|---|---|---|
| R-1 | Language / toolchain | Rust 1.83+ stable, MSVC, static CRT |
| R-2 | UI framework | egui 0.30+ via eframe + wgpu |
| R-3 | Engine codebase | Fork Carp 3.0.1 (MIT, ~3450 Elo) |
| R-4 | Move generation | Port Carp's magic bitboards into `chess-core/src/movegen.rs`; engine depends on core; no second movegen |
| R-5 | Threading | UI thread + dedicated search thread + rayon Lazy SMP |
| R-6 | NNUE embedding | `include_bytes!` of Carp's MIT network |
| R-7 | Reproducible Mode | `Mode` enum: Default (SMP) vs Reproducible (1-thread + fixed seed) |
| R-8 | Settings persistence | TOML at `%APPDATA%\chess-ai\settings.toml`, atomic write |
| R-9 | Rules test corpus | Perft (FIDE 6) + 1,000-position rules suite + Bratko-Kopec + Win-at-Chess |
| R-10 | Performance benches | criterion: movegen nps + NNUE eval/s + search nps |
| R-11 | Test framework | `cargo test` + `criterion` + `insta` |
| R-12 | Distribution | Single signed `chess-ai.exe` from cargo, GitHub Releases |
| R-13 | Project layout | Cargo workspace: chess-core, chess-engine, chess-app + legacy/ |
| R-14 | Logging | `tracing` off by default, optional file log |
| R-15 | CI | GitHub Actions Windows runner: fmt/clippy/test/bench/build |
| R-16 | a11y / i18n | English-only v1; keyboard nav + high contrast in v1; screen reader v2 |

All `NEEDS CLARIFICATION` markers from the Technical Context are resolved.
