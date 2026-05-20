# Phase 0 Research: Rust-to-C++26 Migration

**Feature**: `002-rust-cpp26-rewrite` · **Spec**: [spec.md](./spec.md) · **Date**: 2026-05-20

Resolves all planning-phase technology questions. Format: **Decision → Rationale → Alternatives considered**.

---

## R-1. C++ standard and toolchain gate

**Decision**: **C++26 via official vendor flags only for production.** Require **both** MSVC and Clang to expose finalized `/std:c++26` (or documented equivalent) before production code ships. **Preview/scaffold** uses MSVC `/std:c++latest` and Clang `-std=c++2c` with `CHESS_PREVIEW_BUILD=ON`.

**Rationale**:
- Locked by Clarifications session 2026-05-20 (Q1–Q3).
- Dual-compiler requirement (Q2) catches portability bugs early and satisfies SC-013.
- Preview mode unblocks directory scaffolding and API stubs while the gate is active.
- Current host audit (2026-05-20): VS 2022 Build Tools 14.44 present; `/std:c++26` not listed; Clang/CMake/Ninja not on PATH — status **Blocked — Awaiting Toolchain** for production.

**Alternatives considered**:
- **Ship on `/std:c++latest` permanently** — violates clarified gate. Rejected.
- **MSVC-only gate** — violates Q2. Rejected.
- **Wait with zero repo changes** — violates Q3 (preview allowed). Rejected.

---

## R-2. UI framework (C++)

**Decision**: **Dear ImGui 1.91+ rendered via SDL2 2.30+** (both vendored under `cpp/third_party/`, static-linked into `chess-ai.exe`).

**Rationale**:
- Mirrors Rust product's **immediate-mode** egui pattern — lowest conceptual migration cost for board redraw, drag-and-drop, modals.
- Constitution permits native/local UI; no browser layer.
- Static SDL2 + ImGui keeps a single portable `.exe` without Qt runtime redistribution.
- Meets FR-016–FR-018 interaction model from [contracts/ui-interactions.md](./contracts/ui-interactions.md).

**Alternatives considered**:
- **Qt 6** — polished native widgets but adds ~15–25 MB runtime/redist complexity and retained-mode board code unlike egui. Rejected for v1 migration velocity.
- **Win32-only** — minimal deps but months of custom board rendering. Rejected.
- **Slint** — separate DSL; poor fit for incremental Rust parity port. Rejected.

---

## R-3. Engine — codebase strategy

**Decision**: **Port the existing integrated Carp fork** from `crates/chess-engine-carp` and adapter layer in `crates/chess-engine` into `cpp/chess_engine/`, preserving algorithms (alpha-beta, ID, TT, LMR, null-move, Lazy SMP, NNUE). Reuse `crates/chess-engine-chess` movegen/rules patterns in `cpp/chess_core/`.

**Rationale**:
- Rust product already selected Carp (GPL-3.0, ~3450 Elo) — port preserves SC-001/SC-002 targets without re-selecting an engine.
- Source-level integration satisfies FR-008 and Constitution II.
- Rust modules map 1:1 to C++ translation units for parity testing (SC-011).
- NNUE `net.bin` copied from `crates/chess-engine-chess/bins/net.bin` (same path Rust uses via `include_bytes!` in `nnue/mod.rs`); embedded at build time per R-6. Auxiliary movegen tables (`sliders.bin`, `bishop_magics.bin`, etc.) copied from the same `bins/` directory into `cpp/chess_core/bins/` during core port (T013).

**Alternatives considered**:
- **Link Rust staticlib via FFI** — keeps Rust runtime; not a C++26 rewrite. Rejected.
- **Replace with Stockfish C++** — different codebase; parity harness useless; GPL-3.0 still applies but migration risk higher. Rejected for v1.
- **New C++ engine from scratch** — violates schedule and SC-001. Rejected.

---

## R-4. Move generation and rules home

**Decision**: **`chess_core` owns movegen and FIDE rules** — port magic bitboards from `chess-engine-chess` / `chess-core` into `cpp/chess_core/`. `chess_engine` depends on `chess_core` only; `chess_app` depends on both.

**Rationale**:
- Same dependency direction as Rust workspace prevents UI/engine move legality divergence (FR-005/FR-006).
- Shared corpora (`perft_suite.epd`, `rules_v1.epd`) run against both implementations.

**Alternatives considered**:
- **Separate movegen inside engine** — dual implementation risk. Rejected.

---

## R-5. Build system and dual release

**Decision**: **CMake 3.28+ with Ninja generator.** `CMakePresets.json` defines:

| Preset | Compiler | Standard (preview) | Standard (production) |
|--------|----------|--------------------|------------------------|
| `msvc-preview` | MSVC | `/std:c++latest` | blocked |
| `clang-preview` | Clang | `-std=c++2c` | blocked |
| `msvc-release` | MSVC | — | `/std:c++26` |
| `clang-release` | Clang | — | `-std=c++26` |

`cmake/ToolchainGate.cmake` fails configure on `*-release` presets if official C++26 flag unsupported.

**Rationale**: Clarifications Q4–Q5 (dual release + CMake+Ninja). Single project definition avoids duplicate build files (SC-014).

**Alternatives considered**:
- **Separate VS `.sln` + ad hoc Clang build** — violates FR-025. Rejected.
- **Bazel** — unnecessary complexity for 3-target desktop app. Rejected.

---

## R-6. NNUE network embedding

**Decision**: Embed `net.bin` via CMake custom command producing `network_data.cpp` with `alignas(64) std::byte network[]`. Source: `crates/chess-engine-chess/bins/net.bin`. GPL-3.0 LICENSE copied to `cpp/chess_engine/UPSTREAM-LICENSE` from `crates/chess-engine-chess/UPSTREAM-LICENSE`.

**Rationale**: Matches Rust `include_bytes!` behavior; satisfies FR-021 single-artifact distribution.

**Alternatives considered**:
- **Sidecar file next to exe** — runtime load failure risk. Rejected.

---

## R-7. Threading model

**Decision**: **UI thread (main / SDL event loop)** + **one search thread** owning `Engine`, with `std::jthread` worker pool for Lazy SMP in default mode; single-thread + fixed seed in Reproducible Mode.

**Rationale**: Direct port of Rust `crossbeam-channel` design using `std::mutex` + `std::condition_variable` bounded queue (capacity 8) per engine-api contract.

**Alternatives considered**:
- **OpenMP** — extra dependency; Carp already uses explicit threads. Rejected.
- **Search on UI thread** — UI freeze. Rejected.

---

## R-8. Settings persistence

**Decision**: **toml++** parsing/serialization; path `%APPDATA%\chess-ai\settings.toml`; atomic write via temp file + `rename` (same semantics as Rust).

**Rationale**: Byte-compatible schema with [contracts/settings-file.md](./contracts/settings-file.md) copied from 001; users migrating builds keep settings.

**Alternatives considered**:
- **JSON** — breaks settings file parity. Rejected.

---

## R-9. Testing and parity harness

**Decision**: **GoogleTest** for C++ tests; **CTest** orchestration; **parity harness** scripts invoke Rust `cargo test -- --format json` (or structured snapshots) and C++ gtest output, diff per corpus.

**Rationale**: Satisfies FR-023 and SC-011 (95% test equivalence). Reuses existing EPD files under `crates/*/tests/corpora/`.

**Alternatives considered**:
- **Catch2 only** — fine but team standardizes on GTest for structured matchers. Either works; GTest chosen for MSVC/Clang CI familiarity.

---

## R-10. Toolchain readiness automation

**Decision**: `scripts/toolchain-readiness.ps1` verifies/installs (via winget/choco where available): CMake, Ninja, LLVM Clang, VS Build Tools workload; `scripts/smoke-cpp26.ps1` probes both compilers for official C++26 flag and writes `docs/toolchain-gate-status.json`.

**Rationale**: Host audit showed CMake/Ninja/Clang missing; gate documentation required by FR-001a.

**Alternatives considered**:
- **Manual README only** — insufficient for SC-012 reproducibility. Rejected.

---

## R-11. Rust archive migration path

**Decision**: After SC-011, move `crates/` → `legacy/rust-crates/` with README pointer; remove from default CMake workflow; retain for one release cycle for parity reruns.

**Rationale**: FR-024; keeps history without dual-active development.

**Alternatives considered**:
- **Delete Rust immediately** — loses parity oracle. Rejected.

---

## R-12. Static linking and distribution

**Decision**: MSVC release: `/MT` static CRT where compatible; SDL2 and ImGui built as static CMake targets; `/SUBSYSTEM:WINDOWS` with console alloc for `--self-test` only via subsystem trick or dual entry — prefer **console subsystem** with `--gui` default (matches Rust CLI ergonomics).

**Rationale**: FR-022 portable `.exe`; avoids VC++ redist requirement where possible.

**Alternatives considered**:
- **Dynamic SDL2.dll** — extra artifact; violates single-file spirit. Rejected.

---

## R-13. Dependency minimalism

**Decision**: Vendored third_party: ImGui, SDL2, toml++, fmt, googletest (tests only). No Boost, no Qt, no networking libraries.

**Rationale**: Constitution V; FR-020.

**Alternatives considered**: See R-2, R-5.

---

## R-14. Binary embedding (`#embed`)

**Decision**: **Primary**: C++26 `#embed` for `net.bin`, magic bitboard blobs, and `pieces.png`. **Fallback**: `cmake/EmbedNetwork.cmake` (and siblings) generate `constexpr` byte arrays when `CHESS_HAS_EMBED` is false.

**Rationale**: Replaces Rust `include_bytes!` and ad-hoc code generators; satisfies C26-1 and FR-022 single-exe distribution.

**Alternatives considered**:
- **Runtime file I/O** — breaks offline single-exe and SC-007. Rejected.
- **CMake-only forever** — works but ignores headline C++26 feature. Rejected for release.

---

## R-15. Contracts on public APIs

**Decision**: All functions in `*/contracts/public_api.hpp` use `[[pre]]` / `[[post]]` when `CHESS_HAS_CONTRACTS`; otherwise documented `assert` + tests in debug.

**Rationale**: Move legality, queue bounds, and time-control validation are safety-critical; aligns with C++26 contracts theme and C26-2.

**Alternatives considered**:
- **Documentation-only** — no runtime/static analysis benefit. Rejected.

---

## R-16. Static reflection for settings (optional)

**Decision**: When `CHESS_HAS_REFLECTION`, generate settings metadata and TOML walk from `UserSettings`; else maintain `settings_schema.hpp` manual table.

**Rationale**: Reduces schema drift vs 001; reflection compilers still maturing (HLD §3.4).

**Alternatives considered**:
- **External codegen (e.g. protoc)** — extra tool chain. Rejected.
- **Require reflection for v1** — blocks preview builds. Rejected.

---

## R-17. `constexpr` compile-time tables

**Decision**: Zobrist keys, attack/magic tables, and `Move` codec MUST be `constexpr`-friendly; prefer `std::array` + embedded bytes over `.inc` assembly.

**Rationale**: C++26 constexpr expansion; zero runtime init; earlier error detection.

**Alternatives considered**:
- **Runtime initialization** — slower startup; harder constexpr tests. Rejected.

---

## R-18. Concurrency model — defer `std::execution`

**Decision**: v1 uses **main thread + `std::jthread` search worker + bounded `std::deque` command queue** (depth 8). **Do not** adopt `std::execution` senders/receivers in v1.

**Rationale**: Desktop offline monolith; parity with Rust `crossbeam-channel`; Constitution V minimal deps (HLD §3.3).

**Alternatives considered**:
- **`std::execution` for engine events** — powerful but immature ecosystem and mismatched problem shape. Deferred to v2 (network/GPU).
