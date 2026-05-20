# Implementation Plan: Rust-to-C++26 Chess Migration

**Branch**: `002-rust-cpp26-rewrite` | **Date**: 2026-05-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-rust-cpp26-rewrite/spec.md`

## Summary

Reimplement the constitution-compliant chess product (currently in Rust under `crates/`) as a **C++26** modular monolith: `chess-core` (rules/movegen), `chess-engine` (ported Carp NNUE search), and `chess-app` (Dear ImGui + SDL2 native UI) built by **CMake + Ninja** with **dual release** configurations for **MSVC** and **Clang**. Behavioral parity with the Rust reference is validated via shared test corpora and automated diff harnesses (SC-011).

**C++26 is a design requirement**, not only a compiler flag: adopt `#embed`, contracts, constexpr tables, optional static reflection (settings), optional `std::simd` (NNUE), and `std::expected` per [hld.md §3](./hld.md#3-c26-language-feature-architecture-hld) and [lld.md §2](./lld.md#2-c26-feature-application-lld). Defer `std::execution` to v2 ([research.md R-18](./research.md#r-18-concurrency-model--defer-stdexecution)).

**Migration is gated** until both compilers ship official `/std:c++26` flags (Clarifications 2026-05-20). Until then, only **preview/scaffold** code is permitted under MSVC `/std:c++latest` and Clang `-std=c++2c`, plus toolchain-readiness automation.

## Technical Context

**Language/Version**: C++26 (official `/std:c++26` on MSVC and Clang after gate clears). Preview phase: MSVC `/std:c++latest`, Clang `-std=c++2c` with `CHESS_PREVIEW_BUILD=ON`.
**Primary Dependencies**: CMake 3.28+, Ninja 1.11+, Dear ImGui 1.91+ (vendored), SDL2 2.30+ (static link), toml++ 3.8+ (settings), GoogleTest 1.15+ (tests), `{fmt}` 11+ (logging), ported Carp NNUE engine (GPL-3.0, weights via C++26 `#embed` with CMake-generated fallback per [R-14](./research.md#r-14-binary-embedding-embed)).
**Storage**: One TOML file at `%APPDATA%\chess-ai\settings.toml` (~1 KB, atomic write). No game persistence.
**Testing**: GoogleTest/CTest for unit/integration; parity harness diffing Rust `cargo test` JSON output vs C++ test runners; existing EPD/perft corpora from `crates/chess-core/tests/corpora/` and `crates/chess-engine/tests/corpora/`.
**Target Platform**: Windows 10/11 x86-64 only (v1). Dual release `.exe` artifacts (MSVC and Clang builds); either may ship per release if both CI-green.
**Project Type**: Single desktop application — CMake workspace with static libraries `chess_core`, `chess_engine`, executable `chess-ai`.
**Performance Goals**: Inherit Rust targets: ≥ 3400 Elo strength; cold start ≤ 5 s; time overshoot ≤ 200 ms; idle CPU 0%; RAM ≤ 500 MB; movegen ≥ 100M nps; search ≥ 1.5M nps @ 8 threads.
**Constraints**: Constitution I–V; toolchain gate (FR-001/FR-001a); dual compiler CI (FR-022a/SC-013); CMake+Ninja only (FR-025); no runtime network (FR-020); preview code non-shipping until gate clears.
**Scale/Scope**: One human + one AI (or AI vs AI). One active game. Rust tree archived after SC-011.

All Technical Context fields are filled. **No `NEEDS CLARIFICATION` markers remain** — see [research.md](./research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md).

| Principle | Status | Evidence |
|---|---|---|
| **I. Single-Executable Distribution** | PASS | One `chess-ai.exe` per compiler build; NNUE embedded at link time; static SDL2 + CRT where applicable. [R-6, R-12, quickstart §2] |
| **II. Single Integrated Engine** | PASS | One `chess_engine` library; no UCI subprocess; in-process command/event API per [contracts/engine-api.md](./contracts/engine-api.md). [R-3] |
| **III. Native UI Only** | PASS | Dear ImGui + SDL2; no browser/webview/Electron. [R-2, contracts/ui-interactions.md] |
| **IV. Deterministic / Reproducible** | PASS | `ReproducibleMode` single-thread + fixed seed; default multi-thread SMP. [R-7, contracts/engine-api.md §6] |
| **V. Minimalism** | PASS | 3 CMake targets (`chess_core`, `chess_engine`, `chess-ai`); no DB, no plugins, no second engine. [R-13] |

**Result**: All five gates pass. **No Complexity Tracking justifications required.**

## Project Structure

### Documentation (this feature)

```text
specs/002-rust-cpp26-rewrite/
├── hld.md               # High-level design (architecture, C++26 adoption §3, deployment)
├── lld.md               # Low-level design (C++26 patterns §2, UML class/sequence/state)
├── plan.md              # This file
├── research.md          # Phase 0 research — toolchain, UI, engine port, build
├── data-model.md        # Phase 1 — C++ entity mapping (parity with 001)
├── quickstart.md        # Phase 1 — build, test, toolchain gate checks
├── contracts/
│   ├── engine-api.md       # UI ↔ Engine in-process C++ API
│   ├── settings-file.md    # TOML schema (parity with 001)
│   ├── cli-flags.md        # CLI contract
│   └── ui-interactions.md  # User-facing UI contract
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
chess/
├── CMakeLists.txt                  # root; options: CHESS_PREVIEW_BUILD, CHESS_TOOLCHAIN_GATE
├── CMakePresets.json               # msvc-release, clang-release, msvc-preview, clang-preview
├── cmake/
│   ├── ToolchainGate.cmake         # /std:c++26 gate + CHESS_HAS_* feature probes
│   ├── EmbedNetwork.cmake          # fallback when #embed unavailable (preview)
│   ├── SimdProbe.cmake             # AVX2/SSE2 + CHESS_HAS_SIMD
│   └── ParityHarness.cmake         # optional: invoke Rust tests for diff
├── cpp/
│   ├── chess_core/                 # Position, Move, Game, movegen, rules, SAN
│   │   ├── include/chess_core/
│   │   ├── bins/                   # magics, sliders, etc. from chess-engine-chess/bins/
│   │   └── src/
│   ├── chess_engine/               # Ported Carp search + NNUE + TT + SMP
│   │   ├── include/chess_engine/
│   │   ├── bins/net.bin            # copied from crates/chess-engine-chess/bins/net.bin
│   │   ├── UPSTREAM-LICENSE        # from crates/chess-engine-chess/UPSTREAM-LICENSE (GPL-3.0)
│   │   └── src/
│   └── chess_app/                  # SDL2 + ImGui UI, settings, CLI, engine_link
│       ├── include/chess_app/
│       ├── src/
│       └── assets/
├── tests/
│   ├── core/                       # perft, rules (EPD corpora)
│   ├── engine/                     # legality, tactics, reproducible, time
│   └── parity/                     # rust-vs-cpp output diff runners
├── scripts/
│   ├── toolchain-readiness.ps1     # install/check CMake, Ninja, Clang, MSVC
│   └── smoke-cpp26.ps1             # verify official /std:c++26 on both compilers
├── crates/                         # Rust reference (active until SC-011, then → legacy/)
├── specs/002-rust-cpp26-rewrite/
└── legacy/                         # post-migration archive destination for Rust tree
```

**Structure Decision**: New `cpp/` tree is the canonical implementation. Rust `crates/` remains for parity reference until SC-011, then moves under `legacy/rust-crates/`. CMake is the single build definition; Ninja is the required generator for CI and local automation.

## Complexity Tracking

> Not applicable — all constitution gates pass without exceptions.
