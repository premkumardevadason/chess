# Implementation Plan: Chess AI Rewrite (v1)

**Branch**: `001-chess-ai-rewrite` | **Date**: 2026-04-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-chess-ai-rewrite/spec.md`

## Summary

Replace the existing Spring Boot / TypeScript / multi-AI chess application with a **clean-room single-binary native Windows desktop application written in Rust** that ships **one** top-tier NNUE-based chess engine statically linked into the same `.exe`. The engine is a fork of [Carp 3.0.1](https://github.com/dede1751/carp) (MIT, ~3450 Elo CCRL) refactored from a UCI binary into an internal library crate; the GUI is built with [egui](https://github.com/emilk/egui) via `eframe` + `wgpu`. The whole product is one statically linked `chess-ai.exe` (~50–60 MB) with **zero** runtime network traffic, **zero** game-file persistence (in-session undo/redo only), and **zero** runtime dependencies beyond core Windows DLLs. The application targets Windows x86-64 only for v1 (clarification Q5).

## Technical Context

**Language/Version**: Rust 1.83+ stable, MSVC toolchain (`x86_64-pc-windows-msvc`), static CRT (`-C target-feature=+crt-static`)
**Primary Dependencies**: `eframe`/`egui` 0.30+ (GUI), `wgpu` (renderer), `crossbeam-channel` (thread-IPC), `rayon` (Lazy SMP), `serde` + `toml` (settings), `directories` (path resolution), `tracing` (logging), `clap` (CLI parsing), forked Carp engine (vendored as the `chess-engine` crate)
**Storage**: One TOML file at `%APPDATA%\chess-ai\settings.toml` (~1 KB, atomic write). **No** game persistence, **no** database, **no** caches written to disk.
**Testing**: `cargo test` (unit + integration), `criterion` (benchmarks), `insta` (snapshot tests for SAN move-list rendering)
**Target Platform**: Windows 10/11 x86-64 only (v1). Single signed `.exe` artifact, portable mode (run from any folder, no admin/install).
**Project Type**: Single desktop application — Cargo workspace with three local crates (`chess-core`, `chess-engine`, `chess-app`).
**Performance Goals**: ≥ 3400 Elo strength target; cold start ≤ 5 s; default time-control overshoot ≤ 200 ms; idle CPU 0%; steady-state RAM ≤ 500 MB; movegen ≥ 100M nps; search ≥ 1.5M nps with 8 threads.
**Constraints**: Constitution Principle II (single integrated engine, no plugin/UCI bridge), Principle III (native UI, no browser/webview), Principle IV (deterministic Reproducible Mode toggle), Principle V (minimal dependencies — only what's strictly required), no runtime network access (FR-021), no end-user training (Q3).
**Scale/Scope**: Two players (one human + one AI, or AI vs AI for self-play testing). One open game in flight at a time. ≤ 1 settings file, ≤ 7 days of optional debug logs.

All Technical Context fields are filled. **No `NEEDS CLARIFICATION` markers remain** — see [research.md](./research.md) for the full decision log resolving 16 research areas.

## Constitution Check

Evaluated against [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md). The Constitution defines 5 principles; each is evaluated below.

| Principle | Status | Evidence |
|---|---|---|
| **I. Single-Executable Distribution** | PASS | Single `chess-ai.exe` from `cargo build --release --target x86_64-pc-windows-msvc` with static CRT. NNUE network embedded via `include_bytes!`. No installer, no DLLs (verified via `dumpbin /dependents` in quickstart §2). [R-1, R-12, R-13] |
| **II. Single Integrated Engine** | PASS | Exactly one engine — forked Carp — compiled into the application crate as a Rust library dependency. No subprocess, no UCI bridge, no DLL plugin, no engine selector UI. The engine API is a Rust function/channel API inside the same process. [R-3, contracts/engine-api.md] |
| **III. Native UI Only** | PASS | egui via eframe + wgpu. No browser, no webview, no HTML/JS, no Electron, no Tauri. Pure Rust GUI rendered via DirectX 12 / 11 fallback. [R-2, contracts/ui-interactions.md] |
| **IV. Deterministic / Reproducible** | PASS | Reproducible Mode (`Mode::Reproducible { seed }`) gives bit-identical results across runs (single-thread, fixed-seed RNG, fixed move-ordering tiebreaks). Default mode is multi-threaded for strength, with the trade-off explicitly user-controlled and documented. [R-7, FR-009 / FR-009a, contract test §6] |
| **V. Minimalism** | PASS | 3 internal crates (chess-core, chess-engine, chess-app) — the minimum needed to keep concerns separable for testing. No game persistence, no database, no auth, no telemetry, no online features, no second engine, no plugin system. Out-of-scope items are explicitly listed in quickstart §9. [R-13] |

**Result**: All five gates pass. **No Complexity Tracking justifications required.**

## Project Structure

### Documentation (this feature)

```text
specs/001-chess-ai-rewrite/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 — 16 decisions resolving all NEEDS CLARIFICATION
├── data-model.md        # Phase 1 — entities (Position, Move, Game, EngineConfig, …)
├── quickstart.md        # Phase 1 — developer entry: build, test, run, ship
├── contracts/           # Phase 1
│   ├── engine-api.md       # UI ↔ Engine in-process Rust API
│   ├── settings-file.md    # TOML schema and atomicity rules
│   ├── cli-flags.md        # CLI flag contract (--version, --self-test, …)
│   └── ui-interactions.md  # User-facing screen and interaction contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (already passed)
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
chess/
├── Cargo.toml                          # workspace manifest (members = ["crates/*"])
├── Cargo.lock                          # committed
├── rust-toolchain.toml                 # pins stable-x86_64-pc-windows-msvc
├── .cargo/
│   └── config.toml                     # default RUSTFLAGS, cargo aliases
├── crates/
│   ├── chess-core/                     # Pure-logic: board, FIDE rules, move types, movegen
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs
│   │       ├── position.rs             # Position entity (data-model §1)
│   │       ├── moves.rs                # Move + MoveRecord (data-model §2, §3)
│   │       ├── movegen.rs              # magic bitboards (canonical home; engine consumes it)
│   │       ├── game.rs                 # Game + GameResult (data-model §4, §5)
│   │       ├── rules.rs                # Threefold, 50-move, insufficient material
│   │       └── san.rs                  # SAN rendering and parsing
│   ├── chess-engine/                   # Forked Carp 3.0.1: search + NNUE + TT + SMP
│   │   ├── Cargo.toml                  # depends on chess-core for Position / Move / movegen
│   │   ├── nets/
│   │   │   ├── default.bin             # MIT-licensed NNUE network (~30 MB; embedded)
│   │   │   └── LICENSE                 # network's MIT license text
│   │   ├── UPSTREAM-LICENSE            # Carp's MIT license text
│   │   └── src/
│   │       ├── lib.rs                  # public API: Engine, EngineHandle, Command, Event
│   │       ├── search.rs               # alpha-beta + iterative deepening + LMR + null-move
│   │       ├── smp.rs                  # Lazy SMP via rayon
│   │       ├── nnue.rs                 # NNUE inference (AVX2 / SSE2 / scalar)
│   │       ├── tt.rs                   # transposition table
│   │       ├── ordering.rs             # move ordering, killers, history heuristic
│   │       ├── time.rs                 # time management
│   │       ├── repro.rs                # Mode::Reproducible support
│   │       └── thread.rs               # search worker thread + channel plumbing
│   └── chess-app/                      # Binary: egui UI + glue
│       ├── Cargo.toml                  # [[bin]] name = "chess-ai"
│       ├── build.rs                    # bake version/commit/network-hash via vergen
│       └── src/
│           ├── main.rs                 # entry point; CLI parse; eframe::run_native
│           ├── cli.rs                  # clap definitions; --version, --self-test, …
│           ├── settings.rs             # UserSettings (data-model §8) + atomic IO
│           ├── ui/
│           │   ├── mod.rs              # App impl eframe::App
│           │   ├── game_screen.rs      # board, move list, controls
│           │   ├── settings_screen.rs
│           │   ├── about_screen.rs
│           │   ├── board.rs            # piece sprites, drag-and-drop, highlight
│           │   ├── promotion.rs        # promotion modal
│           │   └── theme.rs            # Standard / HighContrast / Dark
│           ├── engine_link.rs          # bridges egui frame to EngineHandle
│           ├── self_test.rs            # logic for --self-test
│           └── assets/
│               ├── pieces.png          # piece sprite atlas (CC0/PD)
│               └── fonts/              # bundled font (e.g., NotoSans, OFL license)
├── tests/                              # workspace-level integration tests
│   ├── perft.rs                        # FIDE 6-position perft suite (R-9)
│   ├── rules.rs                        # 1000+ FIDE rules positions (SC-008)
│   ├── tactics.rs                      # Bratko-Kopec + Win-at-Chess (SC-002)
│   ├── reproducible.rs                 # Reproducible Mode determinism (FR-009)
│   ├── cancel_latency.rs               # Stop-command latency (engine-api §3.7)
│   ├── settings_file.rs                # TOML round-trip + corruption recovery
│   ├── cli.rs                          # --version, --help, --self-test, exit codes
│   ├── ui_smoke.rs                     # egui headless smoke test
│   ├── engine_legal.rs                 # FR-008: engine never returns illegal moves
│   └── corpora/                        # FEN/EPD test data (~2 MB, committed)
│       ├── perft_suite.epd
│       ├── rules_1000.epd
│       ├── bratko_kopec.epd
│       └── win_at_chess.epd
├── benches/                            # criterion benchmarks
│   ├── movegen.rs                      # ≥ 100M nps target
│   ├── nnue.rs                         # ≥ 5M evals/sec/thread target
│   └── search.rs                       # ≥ 1.5M nps at depth 12 / 8 threads target
├── .github/
│   └── workflows/
│       └── ci.yml                      # fmt + clippy + test + bench-no-run + build (R-15)
├── specs/
│   └── 001-chess-ai-rewrite/           # this feature
├── docs/                               # (legacy reference docs — read-only, untouched)
└── legacy/                             # legacy Java + TS + infra (moved here, not built)
    ├── README.md                       # explains: this is the old code, kept for reference
    ├── java-src/                       # was: src/main/java/com/example/chess/...
    ├── frontend/                       # was: src/main/resources/static + frontend/
    ├── infra/                          # was: helm/, terraform/, awsiac/, azure/, k8/
    ├── pom.xml
    ├── package.json
    └── …
```

**Structure Decision**: The chosen layout is **a single Cargo workspace with three local crates** (`chess-core`, `chess-engine`, `chess-app`) producing exactly one binary (`chess-ai.exe`). This is the minimum decomposition that keeps testable concerns separable: the rules layer (`chess-core`) can be unit-tested without the engine; the engine (`chess-engine`) can be benchmarked without the UI; the UI (`chess-app`) can be smoke-tested against a mock engine. Move generation (`movegen.rs`) lives in `chess-core` so the dependency direction is strictly `chess-app → chess-core` and `chess-engine → chess-core` — there is no circular dependency, and there is exactly one move-generator implementation that both UI legality checks and engine search consume. The legacy Spring Boot / TypeScript / Helm / Terraform / AWS / Azure code is moved wholesale into `legacy/` at the repo root and is **not** part of the workspace — it is preserved for historical reference only and `cargo build` does not see it. Rationale and alternatives are documented in [research.md §R-13](./research.md#r-13-project-layout).

## Complexity Tracking

> Not required — Constitution Check has no violations.

All five Constitution principles pass without justification. The plan introduces no exception that would require entries in this section.

---

## Phase 0 status: COMPLETE

[research.md](./research.md) resolves 16 decisions covering language, UI, engine, threading, NNUE network, Reproducible Mode, settings persistence, test corpora, performance benches, distribution, layout, logging, CI, and a11y/i18n. Every Technical Context field above is sourced from a research decision.

## Phase 1 status: COMPLETE

The following Phase 1 artifacts exist:

- [data-model.md](./data-model.md) — 9 entities with fields, validation rules, and state transitions; 5 cross-cutting invariants captured as test obligations.
- [contracts/engine-api.md](./contracts/engine-api.md) — Rust API + channel contract for UI↔Engine, with 6 behavioural guarantees and a 4-test compliance contract.
- [contracts/settings-file.md](./contracts/settings-file.md) — TOML schema, atomicity protocol, migration policy, and a 5-test compliance contract.
- [contracts/cli-flags.md](./contracts/cli-flags.md) — CLI grammar with mutually-exclusive flag rules and 6-test compliance contract.
- [contracts/ui-interactions.md](./contracts/ui-interactions.md) — observable user-facing behaviour: 3 screens, 7 hotkeys, accessibility scope.
- [quickstart.md](./quickstart.md) — developer onboarding from clean Windows checkout to running engine + tests + benches.

Agent context file [`.cursor/rules/specify-rules.mdc`](../../.cursor/rules/specify-rules.mdc) is updated to reference this plan.

## Re-evaluated Constitution Check (post-design)

After completing Phase 1 design, I re-ran the Constitution gates against the concrete artifacts:

| Principle | Re-check | Notes |
|---|---|---|
| I. Single-Executable | PASS | quickstart.md §2 demonstrates the static-CRT build producing one DLL-free `.exe`. Network blob is `include_bytes!`'d. |
| II. Single Integrated Engine | PASS | engine-api.md is an in-process Rust API; no UCI/IPC/plugin surfaces exist anywhere in the Project Structure. |
| III. Native UI Only | PASS | ui-interactions.md describes egui screens; no `webview`, `tauri`, `cef` crate appears in the Cargo.toml dependency list (verified inline). |
| IV. Deterministic / Reproducible | PASS | reproducible.rs in tests/ exercises bit-for-bit determinism over 50 positions. data-model.md §6 + research.md R-7 give the technical mechanism. |
| V. Minimalism | PASS | Three workspace crates, no second engine, no telemetry, no game persistence, no database. quickstart.md §9 explicitly enumerates and rejects all out-of-scope features. |

**No regressions introduced by the design.** Plan is ready to proceed to `/speckit.tasks`.

## Next steps

1. Run `/speckit.tasks` to generate the dependency-ordered `tasks.md`.
2. Run `/speckit.analyze` to cross-validate the spec, plan, and tasks for consistency before implementation.
3. Optionally run `/speckit.checklist` to add implementation-quality checks beyond the existing `requirements.md`.
4. Begin implementation per `tasks.md` and `quickstart.md`.
