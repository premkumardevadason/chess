# Tasks: Rust-to-C++26 Chess Migration

**Input**: Design documents from `/specs/002-rust-cpp26-rewrite/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included — spec FR-023 and SC-011 require an automated parity test suite versus the Rust reference.

**Organization**: Tasks grouped by user story. **Preview/scaffold** work may proceed under `/std:c++latest` / `-std=c++2c`; production tasks marked **(PROD)** require official `/std:c++26` gate clearance.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 0: Design (HLD / LLD) — prerequisite for implementation

**Purpose**: Architecture and detailed design with UML before writing production C++26 code.

**Deliverables** (complete):

| Document | Path | Contents |
|----------|------|----------|
| High-Level Design | [hld.md](./hld.md) | Context, **C++26 adoption §3**, components, threads, build/deploy |
| Low-Level Design | [lld.md](./lld.md) | **C++26 patterns §2**, class/sequence/state/activity UML (Mermaid) |

- [x] T092 Create [hld.md](./hld.md) — system context, **C++26 feature architecture §3**, layered architecture, component diagram, deployment, build presets
- [x] T093 Create [lld.md](./lld.md) — **§2 C++26 feature application** (`#embed`, contracts, reflection, constexpr, SIMD); package + class diagrams; sequences; state machines
- [ ] T094 Engineering review sign-off on HLD/LLD (lead architect + one implementer per layer: core, engine, app) — include C++26 adoption matrix review
- [ ] T095 Traceability pass: map FR-001–FR-025, US1–US4, and C26-1–C26-4 to HLD §3/§13 and LLD §2/§7–§15

**Checkpoint**: T094 approved — then start Phase 1 Setup.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: CMake workspace, toolchain scripts, third-party deps, directory skeleton.

- [ ] T001 Create root `CMakeLists.txt` with options `CHESS_PREVIEW_BUILD`, `CHESS_TOOLCHAIN_GATE`, and subdir `cpp/`, `tests/`
- [ ] T002 Create `CMakePresets.json` with presets `msvc-preview`, `clang-preview`, `msvc-release`, `clang-release` per plan.md
- [ ] T003 [P] Create `cmake/ToolchainGate.cmake` to fail configure on `*-release` presets when `/std:c++26` unavailable; probe and define `CHESS_HAS_EMBED`, `CHESS_HAS_CONTRACTS`, `CHESS_HAS_REFLECTION`, `CHESS_HAS_SIMD` per [lld.md §2.1](./lld.md#21-feature-detection-and-build-integration)
- [ ] T004 [P] Create `cmake/EmbedNetwork.cmake` to generate `network_data.cpp` from `cpp/chess_engine/bins/net.bin` (source: `crates/chess-engine-chess/bins/net.bin`)
- [ ] T005 [P] Create `cmake/ParityHarness.cmake` to wire optional Rust `cargo test` invocation from CTest
- [ ] T006 Create `scripts/toolchain-readiness.ps1` to verify/install CMake, Ninja, Clang, VS Build Tools
- [ ] T007 Create `scripts/smoke-cpp26.ps1` writing `docs/toolchain-gate-status.json` after probing MSVC and Clang C++26 flags
- [ ] T084 Create `scripts/audit-offline.ps1` scaffolding packet-capture + filesystem audit for SC-007 (used by T082)
- [ ] T008 [P] Vendor Dear ImGui under `cpp/third_party/imgui/` with `CMakeLists.txt` static target
- [ ] T009 [P] Vendor SDL2 under `cpp/third_party/sdl2/` with static CMake target for Windows x86-64
- [ ] T010 [P] Vendor toml++ under `cpp/third_party/tomlplusplus/` header-only target
- [ ] T011 [P] Vendor `{fmt}` under `cpp/third_party/fmt/` and GoogleTest under `cpp/third_party/googletest/`
- [ ] T012 [P] Create skeleton `cpp/chess_core/CMakeLists.txt`, `include/chess_core/`, `src/` directories
- [ ] T013 [P] Create skeleton `cpp/chess_engine/CMakeLists.txt`; copy `net.bin` from `crates/chess-engine-chess/bins/net.bin` to `cpp/chess_engine/bins/net.bin`; copy `UPSTREAM-LICENSE` from `crates/chess-engine-chess/UPSTREAM-LICENSE`; copy auxiliary `bins/*.bin` (magics, sliders, etc.) to `cpp/chess_core/bins/` for movegen port per research.md R-3/R-6
- [ ] T014 [P] Create skeleton `cpp/chess_app/CMakeLists.txt`, `assets/pieces.png` (copy from `crates/chess-app/assets/`)
- [ ] T015 Create `tests/CMakeLists.txt` registering `core/`, `engine/`, `parity/` subdirectories

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core types, movegen, rules, engine API shell, app entrypoint — blocks all user stories.

**⚠️ CRITICAL**: No user story work until this phase checkpoint passes.

- [ ] T096 [P] Create `cpp/chess_core/include/chess_core/c26_config.hpp` and `*/contracts/public_api.hpp` skeletons with `CHESS_PRE`/`CHESS_POST` macros per [lld.md §2.1–§2.3](./lld.md#23-contracts--public-api-safety-must)
- [ ] T016 [P] Implement `cpp/chess_core/include/chess_core/types.hpp` (`Color`, `Square`, `PieceType`, `Bitboard`)
- [ ] T017 [P] Implement `cpp/chess_core/include/chess_core/move.hpp` and `src/move.cpp` (16-bit encoding per data-model.md)
- [ ] T018 Implement `cpp/chess_core/include/chess_core/position.hpp` and `src/position.cpp` (Zobrist, castling, EP)
- [ ] T019 Implement `cpp/chess_core/src/movegen.cpp` porting magic bitboards from `crates/chess-engine-chess/src/movegen/`
- [ ] T020 Implement `cpp/chess_core/src/rules.cpp` (check, mate, stalemate, draws) from `crates/chess-core/src/rules.rs`
- [ ] T021 Implement `cpp/chess_core/include/chess_core/move_record.hpp` and undo fields in `src/game.cpp`
- [ ] T022 Implement `cpp/chess_core/include/chess_core/game.hpp` and `src/game.cpp` (`make_move`, terminal detection)
- [ ] T023 Implement `cpp/chess_core/src/san.cpp` SAN render/parse from `crates/chess-core/src/san.rs`
- [ ] T024 Create `tests/core/perft_test.cpp` using `crates/chess-core/tests/corpora/perft_suite.epd`
- [ ] T025 Create `tests/core/rules_test.cpp` using `crates/chess-core/tests/corpora/rules_v1.epd`
- [ ] T026 Implement `cpp/chess_engine/include/chess_engine/engine.hpp` and `src/engine.cpp` skeleton per `contracts/engine-api.md`
- [ ] T027 Implement `cpp/chess_engine/src/command_queue.cpp` (bounded queue capacity 8, `Command`/`Event` enums)
- [ ] T087 [P] Create `tests/engine/no_io_test.cpp` mirroring `crates/chess-engine/tests/no_io.rs` (engine crate must not use filesystem/network APIs)
- [ ] T097 [P] Implement `#embed` path in `cpp/chess_engine/include/chess_engine/nnue_weights.hpp` when `CHESS_HAS_EMBED`; else wire T004 fallback per [lld.md §2.2](./lld.md#22-embed--binary-assets-must-for-release)
- [ ] T028 Wire `cmake/EmbedNetwork.cmake` into `cpp/chess_engine/CMakeLists.txt` as `#embed` fallback only
- [ ] T098 [P] Create `tests/engine/embed_test.cpp` asserting identical NNUE hash for `#embed` vs CMake-fallback paths (C26-4)
- [ ] T029 Implement `cpp/chess_app/src/main.cpp` SDL2 + ImGui init and empty frame loop
- [ ] T030 Implement `cpp/chess_app/src/cli.cpp` parsing flags per `contracts/cli-flags.md`
- [ ] T031 Create `tests/parity/README.md` and `tests/parity/rust_cpp_diff.ps1` harness scaffolding
- [ ] T032 Verify `cmake --preset msvc-preview` and `cmake --build --preset msvc-preview` produce `chess-ai.exe`

**Checkpoint**: Perft and rules tests pass in C++; empty GUI launches; preview build green.

---

## Phase 3: User Story 1 — Play Same Game Experience (Priority: P1) 🎯 MVP

**Goal**: Human vs AI playable game with legal moves, AI replies, correct termination — parity with Rust US1.

**Scope note (C2)**: US1 uses a **minimal legal-move search** (T036) for preview integration only. It does **not** satisfy FR-009, SC-001, or Constitution III strength targets. Shipping requires the **Release Gate** (see below): full NNUE/SMP (T067–T068), tactics (T075), and Elo match (T083).

**Independent Test**: Offline launch, Human vs AI as White, 30+ legal moves, AI responds within time budget, mate-in-2 test announces winner (spec US1). Promotion: play a pawn to the back rank and complete promotion via modal (T062).

### Tests for User Story 1

- [ ] T033 [P] [US1] Create `tests/engine/engine_legal_test.cpp` mirroring `crates/chess-engine/tests/engine_legal.rs`
- [ ] T034 [P] [US1] Create `tests/app/ui_smoke_test.cpp` verifying SDL window creation and one-frame render
- [ ] T035 [US1] Add CTest `parity` target diffing perft output Rust vs C++ for 6 FIDE positions in `tests/parity/perft_diff.cpp`

### Implementation for User Story 1

- [ ] T036 [US1] Port minimal search loop in `cpp/chess_engine/src/search.cpp` (legal move always returned)
- [ ] T037 [US1] Implement `cpp/chess_engine/src/time.cpp` per-move budget enforcement
- [ ] T038 [US1] Implement `cpp/chess_engine/src/engine_handle.cpp` worker thread + `StartSearch`/`SearchComplete` events
- [ ] T039 [P] [US1] Implement `cpp/chess_app/src/ui/board.cpp` piece rendering and square highlights per `contracts/ui-interactions.md` §2.1
- [ ] T040 [US1] Implement `cpp/chess_app/src/ui/game_screen.cpp` Human vs AI turn loop
- [ ] T041 [US1] Implement `cpp/chess_app/src/engine_link.cpp` bridging UI to `EngineHandle`
- [ ] T042 [US1] Implement illegal-move rejection with status message in `cpp/chess_app/src/ui/game_screen.cpp`
- [ ] T085 [US1] Implement game controls in `cpp/chess_app/src/ui/game_screen.cpp`: New Game, Resign, Switch Sides per `contracts/ui-interactions.md` §2.3 (FR-015)
- [ ] T086 [US1] Implement keyboard SAN entry (Tab, type move, Enter) in `cpp/chess_app/src/ui/game_screen.cpp` per `contracts/ui-interactions.md` §2.1 (FR-017)
- [ ] T062 [US1] Implement promotion modal in `cpp/chess_app/src/ui/promotion.cpp` per `contracts/ui-interactions.md` §2.1 (required for legal promotion before US1 checkpoint)
- [ ] T043 [US1] Implement checkmate/stalemate/draw modals in `cpp/chess_app/src/ui/game_screen.cpp`
- [ ] T044 [US1] Wire `chess_app` CMake target linking `chess_core`, `chess_engine`, SDL2, ImGui statically

**Checkpoint**: US1 independent test passes on `msvc-preview` build (**preview MVP only — not release-ready**).

---

## Phase 4: User Story 2 — Strength, Time Control, Settings (Priority: P1)

**Goal**: Strength presets, time controls, Reproducible Mode, persisted settings — parity with Rust US2.

**Independent Test**: Beginner vs Maximum observable strength difference; settings survive restart (spec US2).

### Tests for User Story 2

> **Blocked until Release Gate engine work**: T045 and T046 require T067–T068 (full NNUE + SMP) — do not expect pass on minimal search.

- [ ] T045 [P] [US2] Create `tests/engine/strength_differentiation_test.cpp` from `crates/chess-engine/tests/strength_differentiation.rs` *(after T067–T068)*
- [ ] T046 [P] [US2] Create `tests/engine/time_accuracy_test.cpp` from `crates/chess-engine/tests/time_accuracy.rs` *(after T067–T068)*
- [ ] T047 [P] [US2] Create `tests/app/settings_file_test.cpp` from `crates/chess-app/tests/settings_file.rs`

### Implementation for User Story 2

- [ ] T048 [P] [US2] Implement `cpp/chess_app/include/chess_app/user_settings.hpp` and `src/settings.cpp` (toml++ load/save)
- [ ] T049 [US2] Implement atomic write to `%APPDATA%\chess-ai\settings.toml` per `contracts/settings-file.md`
- [ ] T050 [US2] Implement `cpp/chess_app/src/ui/settings_screen.cpp` for strength/time/thread controls
- [ ] T051 [US2] Implement `cpp/chess_engine/include/chess_engine/config.hpp` with `StrengthPreset` and `TimeControl` variants
- [ ] T052 [US2] Port strength limiting (depth/node scaling) in `cpp/chess_engine/src/search.cpp`
- [ ] T053 [US2] Implement `cpp/chess_engine/src/repro.cpp` Reproducible Mode (single-thread, fixed seed) per `contracts/engine-api.md` §4
- [ ] T054 [US2] Surface Reproducible Mode toggle in `cpp/chess_app/src/ui/settings_screen.cpp` and status panel
- [ ] T055 [US2] Load defaults from settings on startup in `cpp/chess_app/src/main.cpp`

**Checkpoint**: US2 tests pass; settings round-trip verified.

---

## Phase 5: User Story 3 — History, Undo/Redo, Hints (Priority: P2)

**Goal**: Move list, unlimited undo/redo, hints/analysis — parity with Rust US3.

**Independent Test**: 10 moves, undo×3 redo×2, hint with eval+PV, no game files on disk (spec US3).

### Tests for User Story 3

- [ ] T056 [P] [US3] Create `tests/core/move_list_snapshot_test.cpp` from `crates/chess-core/tests/move_list_snapshot.rs`
- [ ] T057 [P] [US3] Create `tests/engine/hint_mate_test.cpp` from `crates/chess-engine/tests/hint_mate.rs`

### Implementation for User Story 3

- [ ] T058 [US3] Extend `cpp/chess_core/src/game.cpp` with undo/redo stacks restoring castling/EP/clocks
- [ ] T059 [US3] Implement move list panel with SAN in `cpp/chess_app/src/ui/game_screen.cpp`
- [ ] T060 [US3] Implement `cpp/chess_engine/src/analysis.cpp` hint/PV search command handling
- [ ] T061 [US3] Add Hint button and analysis display in `cpp/chess_app/src/ui/game_screen.cpp`

**Checkpoint**: US3 independent test passes; undo restores special-move state correctly. (Promotion UI delivered in US1 via T062.)

---

## Phase 6: User Story 4 — AI-vs-AI and Stability (Priority: P3)

**Goal**: Self-play, pause/resume, 24h stability — parity with Rust US4 and SC-009.

**Independent Test**: AI vs AI to terminal state; pause preserves position; no crash in long run (spec US4).

### Tests for User Story 4

- [ ] T063 [P] [US4] Create `tests/engine/ai_vs_ai_test.cpp` from `crates/chess-engine/tests/ai_vs_ai.rs`
- [ ] T064 [US4] Document `tests/engine/stability_24h.md` procedure; add CI nightly job stub in `.github/workflows/chess-stability-nightly.yml`
- [ ] T088 [US4] Create `scripts/run-stability-24h.ps1` executing continuous AI-vs-AI back-to-back games with memory sampling (SC-009); wire workflow to call it

### Implementation for User Story 4

- [ ] T065 [US4] Add AI-vs-AI mode toggle and auto-play loop in `cpp/chess_app/src/ui/game_screen.cpp`
- [ ] T066 [US4] Implement pause/resume stopping search thread in `cpp/chess_app/src/engine_link.cpp`
- [ ] T067 [US4] Port full Carp SMP search in `cpp/chess_engine/src/smp.cpp` from `crates/chess-engine-carp/src/thread.rs`
- [ ] T068 [US4] Port NNUE inference in `cpp/chess_engine/src/nnue.cpp` from `crates/chess-engine-chess/src/nnue/mod.rs`
- [ ] T069 [US4] Implement clean shutdown ≤200 ms in `cpp/chess_app/src/main.cpp` on window close

**Checkpoint**: US4 tests pass; AI-vs-AI completes legal games.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Dual release, parity gate SC-011, toolchain promotion, Rust archive, licensing.

- [ ] T070 [P] Add `clang-preview` build verification to quickstart.md steps in CI/local docs
- [ ] T071 Run `scripts/smoke-cpp26.ps1` and document gate status in `specs/002-rust-cpp26-rewrite/quickstart.md` when flags ship **(PROD)**
- [ ] T072 **(PROD)** Migrate all `cpp/` targets from preview standard flags to official `/std:c++26` in `CMakePresets.json`
- [ ] T073 **(PROD)** Achieve green `msvc-release` and `clang-release` presets (SC-013) on Windows x86-64 CI
- [ ] T074 Create `tests/parity/full_suite.ps1` reporting ≥95% Rust test equivalence (SC-011)
- [ ] T075 [P] Create `tests/engine/tactics_test.cpp` from `crates/chess-engine/tests/tactics.rs` and `corpora/bratko_kopec.epd` *(SC-002; requires T067–T068)*
- [ ] T076 [P] Create `tests/engine/reproducible_test.cpp` from `crates/chess-engine/tests/reproducible.rs`
- [ ] T083 Create `scripts/run-sc001-elo-match.ps1` and `tests/engine/sc001_elo_match.md` documenting 100-game `cutechess-cli` match vs 2500-Elo reference engine (SC-001); run on both `msvc-release` and `clang-release` artifacts before release
- [ ] T077 Implement `cpp/chess_app/src/ui/about_screen.cpp` with GPL-3.0 and network hash per `contracts/cli-flags.md`
- [ ] T078 Implement `--self-test` path in `cpp/chess_app/src/self_test.cpp` per `contracts/cli-flags.md`
- [ ] T079 Move `crates/` to `legacy/rust-crates/` with `legacy/rust-crates/README.md` after SC-011 passes (FR-024)
- [ ] T080 Validate `specs/002-rust-cpp26-rewrite/quickstart.md` end-to-end on clean Windows VM
- [ ] T081 Add compiler tag to `--version` output in `cpp/chess_app/src/cli.cpp` (MSVC vs Clang)
- [ ] T089 [P] Create `scripts/benchmark-startup.ps1` measuring cold start (≤5s) and warm start (≤2s) against SC-005 thresholds; write results to `docs/benchmarks/startup.json`
- [ ] T090 [P] Create `scripts/benchmark-resources.ps1` measuring idle RAM/CPU and search CPU cap (≤500 MB, ≤75% cores) against SC-006; write results to `docs/benchmarks/resources.json`
- [ ] T091 Create `specs/002-rust-cpp26-rewrite/CHANGELOG-parity.md` tracking intentional behavior deltas vs Rust product (≤5 per SC-010) with release-note links
- [ ] T082 Run SC-007 offline audit using `scripts/audit-offline.ps1` (T084) after US3+US4 features enabled; attach report to `docs/audits/offline-sc007.md`

---

## Dependencies & Execution Order

### Phase Dependencies

| Phase | Depends on | Blocks |
|-------|------------|--------|
| 0 Design | — | Phase 1 (T094 sign-off) |
| 1 Setup | Phase 0 | Phase 2 |
| 2 Foundational | Phase 1 | All user stories |
| 3 US1 (P1) | Phase 2 | MVP demo |
| 4 US2 (P1) | Phase 2; soft-deps US1 engine | — |
| 5 US3 (P2) | Phase 2; US1 game loop | — |
| 6 US4 (P3) | US1 engine; US2 config | — |
| 7 Polish | US1–US4 core complete | Release |

### User Story Dependencies

- **US1**: Starts after Foundational — **no dependency** on US2–US4.
- **US2**: Starts after Foundational; integrates with US1 `game_screen` / `engine_link` but testable via settings + engine unit tests alone.
- **US3**: Needs US1 board/game loop; undo/hints extend existing UI.
- **US4**: Needs US1 engine play; benefits from US2 strength presets.

### Within Each User Story

1. Tests before implementation (parity tests may run against partial impl)
2. `chess_core` → `chess_engine` → `chess_app` layer order
3. Story checkpoint before next priority

### Parallel Opportunities

- **Phase 1**: T003–T005, T008–T014 all [P]
- **Phase 2**: T016–T017, T024–T025 after T018 [P] groups
- **After Phase 2**: US2 settings (T048–T050) can parallel US3 undo (T058–T059) if staffed separately
- **Phase 7**: T070, T075–T076, T077 [P]

---

## Parallel Example: User Story 1

```bash
# Tests in parallel:
# T033 tests/engine/engine_legal_test.cpp
# T034 tests/app/ui_smoke_test.cpp

# UI pieces in parallel after engine handle exists:
# T039 cpp/chess_app/src/ui/board.cpp
# T040 cpp/chess_app/src/ui/game_screen.cpp (coordinate merge)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

0. Complete Phase 0: Design review (T094–T095; docs in hld.md / lld.md)
1. Complete Phase 1: Setup (T001–T015)
2. Complete Phase 2: Foundational (T016–T032)
3. Complete Phase 3: User Story 1 (T033–T044, T062, T085–T086)
4. **STOP and VALIDATE**: US1 independent test offline (preview MVP — not shippable)
5. Demo `build/msvc-preview/chess-ai.exe` (preview; non-shipping)
6. Before release: complete **Release Gate** (T067–T068, T075, T083, T074, T072–T073)

### Incremental Delivery

1. Setup + Foundational → core tests green
2. US1 → playable Human vs AI (MVP)
3. US2 → strength/settings
4. US3 → undo/hints
5. US4 → AI-vs-AI + stability
6. Polish → dual release + Rust archive + **(PROD)** C++26 gate

### Toolchain Gate Workflow

| Stage | Tasks | Standard |
|-------|-------|----------|
| Now | T001–T044 (preview) | `/std:c++latest`, `-std=c++2c` |
| When MSVC+Clang ship `/std:c++26` | T071–T073 **(PROD)** | Official C++26 |
| Release | T073–T083, T089–T091, T082, T088 | Compilers + SC-001/005/006/007/009/010 |

### Release Gate (shipping — resolves C2)

Do **not** tag or ship any `*-release` build until **all** of the following are complete:

| Gate | Tasks | Validates |
|------|-------|-----------|
| Engine strength | T067, T068 | FR-009, Constitution III |
| Tactics | T075 | SC-002 |
| Elo match | T083 | SC-001 |
| Parity suite | T074 | SC-011 |
| Dual CI green | T073 | SC-013, FR-022a |
| Official C++26 | T072 | FR-001, SC-012 |
| Performance | T089, T090 | SC-005, SC-006 |
| Offline audit | T082, T084 | SC-007 |
| Stability | T088 | SC-009 |
| Regression budget | T091 | SC-010 |

US1 checkpoint (T044) is a **preview MVP** only — legal moves + UI + minimal search, not customer release.

---

## Notes

- Total tasks: **98** (T092–T093 design docs include C++26 §2/§3; T094–T095 pending review; T096–T098 C++26 infrastructure)
- Design: 4 | US1: 15 | US2: 11 | US3: 6 | US4: 8 | Setup: 16 | Foundational: 18 | Polish: 17
- **[P]** = parallel-safe (different files, no ordering dependency)
- Preview builds MUST NOT ship; tag releases only after **Release Gate** (T067–T068, T075, T083, T074, T072–T073) complete
- Rust `crates/` remains parity oracle until T079
