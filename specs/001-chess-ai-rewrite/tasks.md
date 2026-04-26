---
description: "Tasks for feature 001-chess-ai-rewrite — Chess AI Rewrite (single-binary Rust + NNUE)"
---

# Tasks: Chess AI Rewrite — Single-Executable, Best-in-Class Engine

**Input**: Design documents from `/specs/001-chess-ai-rewrite/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: TDD was not explicitly requested, but the spec's Success Criteria, contracts, and data-model invariants all reference specific test files as part of completion. Test tasks are therefore included as **required implementation tasks** at the end of each phase (not as TDD-first preludes).

**Organization**: Tasks are grouped by user story to enable independent implementation, testing, and demo. US1 is the MVP; US2–US5 build on US1's UI but each is independently testable per its acceptance criteria.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete tasks in this phase)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4, US5). No story tag in Setup, Foundational, or Polish phases.
- All paths are repo-relative (workspace root: `c:\Users\pdevadason\RevEng\chess\`).

## Path Conventions

- **Cargo workspace** at repo root with three local crates per [plan.md](./plan.md#project-structure):
  - `crates/chess-core/` — board, FIDE rules, move types, SAN, Game state machine
  - `crates/chess-engine/` — search, NNUE, transposition table, SMP (forked Carp)
  - `crates/chess-app/` — egui binary, UI screens, settings, engine link
- **Workspace-level integration tests** at `tests/` (repo root)
- **Workspace-level benchmarks** at `benches/` (repo root)
- **Legacy code** at `legacy/` (not part of cargo build)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Move legacy code out of the way and stand up the empty Cargo workspace skeleton.

- [X] T001 Move legacy Java/TS/infra code into `legacy/` directory at repo root: `src/`, `frontend/`, `helm/`, `terraform/`, `awsiac/`, `azure/`, `k8/`, `mcp/`, `pom.xml`, `package.json`, `package-lock.json`, `tsconfig.json`. Keep `docs/` at repo root (read-only reference). Add `legacy/README.md` explaining preservation per [research.md R-13](./research.md#r-13-project-layout).
- [X] T002 Create Cargo workspace manifest at `Cargo.toml` (repo root) with `[workspace] members = ["crates/*"]` and `[workspace.package]` defaults (edition 2021, rust-version 1.83).
- [X] T003 [P] Create `rust-toolchain.toml` at repo root pinning channel `stable`, targets `["x86_64-pc-windows-msvc"]`, components `["rustfmt", "clippy"]` per [research.md R-1](./research.md#r-1-implementation-language-and-toolchain).
- [X] T004 [P] Create `.cargo/config.toml` at repo root with `[build] target = "x86_64-pc-windows-msvc"` and a release profile alias `[alias] release-static = "build --release --target x86_64-pc-windows-msvc"`. Note: do NOT set `+crt-static` globally; leave that to release builds via env var per [quickstart.md §2](./quickstart.md).
- [X] T005 [P] Update `.gitignore` at repo root: ensure `target/`, `Cargo.lock` (committed for binaries — DO NOT ignore), `*.rs.bk` are handled correctly. Add `legacy/target/` and `legacy/node_modules/` to keep legacy build artefacts out.
- [X] T006 [P] Create three empty crates with skeletons:
  - `crates/chess-core/Cargo.toml` + `src/lib.rs` (`pub fn version() -> &'static str { env!("CARGO_PKG_VERSION") }`)
  - `crates/chess-engine/Cargo.toml` + `src/lib.rs` (placeholder)
  - `crates/chess-app/Cargo.toml` + `src/main.rs` (`fn main() { println!("chess-ai placeholder"); }`) with `[[bin]] name = "chess-ai"`.
  Verify `cargo build` succeeds.
- [X] T007 [P] Add `rustfmt.toml` at repo root with `edition = "2021"`, `max_width = 100`. Add `.clippy.toml` at repo root (empty allowed; project relies on `-D warnings`).
- [X] T008 [P] Create `.github/workflows/ci.yml` for Windows runner with the matrix from [research.md R-15](./research.md#r-15-ci--build-automation): `cargo fmt --all -- --check`, `cargo clippy --all-targets -- -D warnings`, `cargo test --release --tests`, `cargo bench --no-run`, `cargo build --release`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Stand up `chess-core` (full FIDE rules + move generation + SAN), bring in the forked Carp engine and refactor it into a library exposing the channel API, embed the NNUE network, and provide the `chess-app` scaffold (CLI parse, eframe boot, settings file, engine link). After this phase, every user story can begin in parallel.

**⚠️ CRITICAL**: No user-story work may begin until this phase is complete and `cargo test --test perft --release` passes.

### chess-core foundation

- [ ] T009 [P] Implement `Position` (bitboards, side-to-move, castling rights, en passant, halfmove/fullmove counters, Zobrist hash) in `crates/chess-core/src/position.rs` per [data-model.md §1](./data-model.md). Include FEN parsing/serialization and `assert_invariants()` debug method.
- [ ] T010 [P] Implement `Move` (16-bit packed `u16`) and `MoveRecord` (with undo data + SAN) in `crates/chess-core/src/moves.rs` per [data-model.md §2 / §3](./data-model.md).
- [ ] T011 Implement magic-bitboard move generator in `crates/chess-core/src/movegen.rs` (porting from forked Carp's `movegen.rs`). Provides `legal_moves(&Position) -> SmallVec<[Move; 256]>` and `is_in_check(&Position) -> bool`. Depends on T009, T010. (Note: this places movegen in `chess-core` so the engine can depend on core, avoiding the circular dep mentioned in [research.md R-4](./research.md#r-4-move-generation-crate).)
- [ ] T012 [P] Implement draw-detection helpers (`is_threefold(history)`, `is_fifty_move(pos)`, `is_insufficient_material(pos)`) in `crates/chess-core/src/rules.rs` per [data-model.md §5](./data-model.md).
- [ ] T013 [P] Implement SAN render (`san_for(&Position, Move) -> SmallString<8>`) and SAN parser (`parse_san(&Position, &str) -> Result<Move>`) in `crates/chess-core/src/san.rs` per [data-model.md §3](./data-model.md).
- [ ] T014 Implement `Game` state machine (`new_game`, `make_move`, `undo`, `redo`, `result()`, `repetition_table`) in `crates/chess-core/src/game.rs` per [data-model.md §4](./data-model.md). Implement `GameResult` and `DrawReason` enums per [data-model.md §5](./data-model.md). Depends on T011, T012, T013.
- [ ] T015 [P] Add `crates/chess-core/src/lib.rs` re-exports: `pub use position::Position; pub use moves::{Move, MoveRecord}; pub use game::{Game, GameMode, GameResult, DrawReason}; pub use movegen::legal_moves;`. Verify `cargo test -p chess-core` passes (with whatever unit tests T009–T014 add).

### chess-engine foundation (vendor Carp + refactor)

- [ ] T016 Vendor [Carp 3.0.1](https://github.com/dede1751/carp) source into `crates/chess-engine/src/` and `crates/chess-engine/nets/`. Copy upstream MIT LICENSE to `crates/chess-engine/UPSTREAM-LICENSE`. Copy bundled NNUE network to `crates/chess-engine/nets/default.bin` and its license to `crates/chess-engine/nets/LICENSE`. Verify license is MIT/Apache-2.0/CC0 per [research.md R-6](./research.md#r-6-nnue-network-embedding-and-licensing) and [FR-006a](./spec.md). Delete Carp's `src/uci.rs` and `src/main.rs` — engine will be a library only.
- [ ] T017 Refactor vendored engine to depend on `chess-core` for `Position`/`Move`/`legal_moves` (replace Carp's `board.rs` and `movegen.rs` with `chess-core` types). Keep Carp's `search.rs`, `nnue.rs`, `tt.rs`, `ordering.rs`, `time.rs` unchanged in algorithm but ported to operate on `chess-core::Position`. Add `crates/chess-engine/Cargo.toml` dependency on `chess-core`. Depends on T015, T016.
- [ ] T018 Embed NNUE network via `include_bytes!("../nets/default.bin")` in `crates/chess-engine/src/nnue.rs`. Replace any file-load path with the embedded byte slice. Verify load succeeds at `Engine::new()`. Depends on T017.
- [ ] T019 Implement public engine API in `crates/chess-engine/src/lib.rs` per [contracts/engine-api.md](./contracts/engine-api.md): `Engine`, `Engine::new()`, `Engine::spawn() -> EngineHandle`, `EngineHandle::send/try_recv/drain`, `Command` enum (`SetPosition`, `StartSearch`, `Stop`, `SetConfig`, `Shutdown`), `Event` enum (`SearchProgress`, `SearchComplete`, `SearchAborted`, `Warning`, `Stopped`). Use `crossbeam-channel` (bounded size 8). Worker thread owns engine state; communicates via channels. Depends on T018.
- [ ] T020 Implement `EngineConfig`, `Mode` (Default/Reproducible), `TimeControl`, `StrengthPreset`, `SearchInfo`, `SearchResult`, `Eval` types in `crates/chess-engine/src/lib.rs` (or `src/config.rs`) per [data-model.md §6 / §7](./data-model.md). Wire `StrengthPreset` to Carp's existing skill-level mechanism.
- [ ] T021 Wire `Stop` command to abort search within 50 ms via shared `AtomicBool` polled in search inner loop per [contracts/engine-api.md §3.7](./contracts/engine-api.md). Depends on T019.

### chess-app foundation (boot + settings + engine link)

- [ ] T022 [P] Add dependencies in `crates/chess-app/Cargo.toml`: `eframe = "0.30"`, `egui = "0.30"`, `chess-core = { path = "../chess-core" }`, `chess-engine = { path = "../chess-engine" }`, `serde = { version = "1", features = ["derive"] }`, `toml = "0.8"`, `directories = "5"`, `clap = { version = "4", features = ["derive"] }`, `tracing = "0.1"`, `tracing-subscriber = { version = "0.3", features = ["env-filter"] }`, `crossbeam-channel = "0.5"`, `humantime = "2"`, `vergen = { version = "8", features = ["build", "git", "gitcl"] }`.
- [ ] T023 [P] Add `crates/chess-app/build.rs` calling `vergen` to expose `VERGEN_GIT_SHA`, `VERGEN_BUILD_TIMESTAMP`, and a custom `CHESS_NETWORK_HASH` env var (sha256 of `../chess-engine/nets/default.bin`) to runtime via `env!`.
- [ ] T024 [P] Implement `UserSettings` struct with `serde` derives and `#[serde(deny_unknown_fields)]` in `crates/chess-app/src/settings.rs` per [data-model.md §8](./data-model.md) and [contracts/settings-file.md §2](./contracts/settings-file.md). Include `EngineSettings`, `UiSettings`, `DiagnosticsSettings` substructs and `default()` impls.
- [ ] T025 Implement settings file IO in `crates/chess-app/src/settings.rs`: `UserSettings::load(path: &Path) -> UserSettings` (with corruption recovery: rename to `.bad-<ts>` and return defaults), `UserSettings::save(&self, path: &Path) -> Result<()>` (write-to-tmp + atomic rename), and `resolve_settings_path(portable: bool) -> PathBuf` using `directories` crate per [contracts/settings-file.md §1, §4, §5](./contracts/settings-file.md). Depends on T024.
- [ ] T026 Implement TimeControl parser in `crates/chess-app/src/settings.rs` for the grammar in [contracts/settings-file.md §3](./contracts/settings-file.md): `PerMove:<dur>`, `Total:<game>:<inc>`, `FixedDepth:<n>`, `FixedNodes:<n>`. On parse failure, fall back to `PerMove:5s` and rewrite the file. Depends on T025.
- [ ] T027 [P] Implement CLI parsing in `crates/chess-app/src/cli.rs` using `clap` derive: `--log-debug`, `--reset-settings`, `--portable`, `--version`, `--help`, `--self-test` per [contracts/cli-flags.md](./contracts/cli-flags.md). Enforce mutually exclusive flags and unknown-flag rejection.
- [ ] T028 Implement `crates/chess-app/src/main.rs` boot sequence: parse CLI → load settings → spawn engine → call `eframe::run_native` (or take CLI exit branch). Empty window placeholder for now. Depends on T019, T026, T027.
- [ ] T029 Implement `crates/chess-app/src/engine_link.rs`: holds `EngineHandle`, exposes `tick(&mut self) -> Vec<Event>` to be called from the egui frame loop, sends `Command`s on UI events. Maintains last-known `engine_status` (Idle / Thinking{depth, eval}). Depends on T019, T028.

### Phase-2 validation tests

- [ ] T030 [P] Add `tests/perft.rs` running FIDE 6-position perft suite (Initial, Kiwipete, Position 3, Position 4, Position 5, Position 6) at depths 6 / 5 / 7 / 5 / 5 / 5 per [research.md R-9](./research.md#r-9-fide-rules-test-corpus). Commit `tests/corpora/perft_suite.epd` with the published node counts.
- [ ] T031 [P] Add `tests/engine_legal.rs` property test: spawn engine, run search at depth 4 over 50 deterministically-shuffled positions (RNG seed `0xDEAD_BEEF`, seed value documented in test header) including all 6 perft positions, assert returned `BestMove.mv` is in `legal_moves(p)`. **Additionally**, for 20 of those positions, repeat the search with `TimeControl::PerMove(1ms)` and assert a legal move is still returned (closes F8 — the pathological-budget edge case from [Edge Cases](./spec.md#edge-cases)). Per [data-model.md cross-cutting invariant 3](./data-model.md) and [FR-008](./spec.md).
- [ ] T032 [P] Add `tests/cancel_latency.rs`: start a 10-second search, sleep 100 ms, send `Stop`, assert `SearchAborted` (or `SearchComplete`) arrives within 50 ms per [contracts/engine-api.md §3.7](./contracts/engine-api.md).

**Checkpoint**: `cargo test --release --tests perft engine_legal cancel_latency` all green. Engine boots, NNUE network loads, perft 5 passes on Kiwipete in < 10 s, search returns legal moves, cancel works. **All five user stories may now begin.**

---

## Phase 3: User Story 1 - Play a Complete Game Against a World-Class AI Opponent (Priority: P1) 🎯 MVP

**Goal**: Launch the executable, see a chessboard, play a full game (white or black) against the built-in AI at default strength, with mouse moves, last-move highlight, legal-target highlights, promotion modal, and game-over modal on terminal states.

**Independent Test**: Launch `chess-ai.exe` on a machine with **no internet connection**. Start a new game as White, play 30+ legal moves, observe AI replies with legal moves within ~5 s each. Verify a forced mate-in-2 setup terminates with checkmate announcement. Verify the spec's [User Story 1 acceptance scenarios 1–5](./spec.md#user-story-1---play-a-complete-game-against-a-world-class-ai-opponent-priority-p1) pass.

### Implementation for User Story 1

- [ ] T033 [P] [US1] Add piece-sprite atlas (CC0/public-domain, e.g. Cburnett SVG rendered to PNG) at `crates/chess-app/src/assets/pieces.png` and font (OFL-licensed, e.g. NotoSans) at `crates/chess-app/src/assets/fonts/NotoSans-Regular.ttf`. Embed via `egui::Context::set_fonts` and `include_bytes!` in `crates/chess-app/src/ui/theme.rs` per [contracts/ui-interactions.md §4](./contracts/ui-interactions.md).
- [ ] T034 [P] [US1] Implement Standard theme (board light/dark squares, highlight colors) in `crates/chess-app/src/ui/theme.rs` per [data-model.md §8](./data-model.md) UiSettings.theme.
- [ ] T035 [P] [US1] Implement `crates/chess-app/src/ui/board.rs` board rendering: 8×8 grid, file/rank labels, piece rendering from sprite atlas, board orientation (WhiteAtBottom / BlackAtBottom), based on `UiState` per [data-model.md §9](./data-model.md). [FR-016](./spec.md), [FR-018](./spec.md).
- [ ] T036 [US1] Wire click-click move input in `crates/chess-app/src/ui/board.rs`: first click selects own piece (if legal), highlights legal targets via `legal_moves(p).filter(from == clicked)`, second click attempts move; if illegal, deselect AND surface the rejection reason from `Game::make_move` (e.g. `"would leave king in check"`, `"blocked by own piece"`) as a transient inline message in the right-panel status area for ~3 s per [contracts/ui-interactions.md §2.1](./contracts/ui-interactions.md). [FR-002](./spec.md), [FR-017](./spec.md), [User Story 1 Acceptance Scenario 2](./spec.md). Depends on T035.
- [ ] T037 [US1] Wire drag-and-drop move input in `crates/chess-app/src/ui/board.rs` per [contracts/ui-interactions.md §2.1](./contracts/ui-interactions.md). [FR-017](./spec.md). Depends on T036.
- [ ] T038 [US1] Implement promotion modal in `crates/chess-app/src/ui/promotion.rs`: 4-button (Q/R/B/N) modal triggered when a pawn move reaches rank 1/8; `Esc` cancels move; result feeds back into `Game::make_move` per [contracts/ui-interactions.md §2.1](./contracts/ui-interactions.md) and [FR-001](./spec.md). Depends on T037.
- [ ] T039 [US1] Implement legal-target highlights and last-move highlight in `crates/chess-app/src/ui/board.rs` per [FR-018](./spec.md) and [contracts/ui-interactions.md §2.1](./contracts/ui-interactions.md). Depends on T036.
- [ ] T040 [US1] Implement check / checkmate / stalemate visual indicator on the board (e.g., red-glow on king square when in check) in `crates/chess-app/src/ui/board.rs` per [FR-018](./spec.md).
- [ ] T041 [US1] Implement `crates/chess-app/src/ui/game_screen.rs` layout: board on left, right panel with side-to-move indicator, engine status, move list (placeholder for US3), captured-piece tray (placeholder for T041a), New Game button, Resign button per [contracts/ui-interactions.md §2.3, §2.5](./contracts/ui-interactions.md) and [FR-019](./spec.md). Depends on T040.
- [ ] T041a [US1] Implement captured-piece tray in `crates/chess-app/src/ui/game_screen.rs`: two horizontal rows (one per color) of small piece icons drawn from the same sprite atlas as T035, ordered Queen→Rook→Bishop→Knight→Pawn, showing all captures by that color in the current game. Update on every `Game::make_move` and `Game::undo`/`redo`. Show net material differential as a small `+N` / `−N` badge next to the side currently ahead. Per [FR-019](./spec.md). Depends on T035, T041.
- [ ] T042 [US1] Wire "New Game" action (with confirm modal if game in progress) in `crates/chess-app/src/ui/game_screen.rs`: resets `Game` to standard starting position, clears history, prompts color choice. [FR-004](./spec.md), [FR-012](./spec.md). Depends on T041.
- [ ] T043 [US1] Wire "Resign" action (with confirm modal) in `crates/chess-app/src/ui/game_screen.rs`: sets `Game.result = GameResult::Resignation(opponent)`, displays game-over modal. [FR-012](./spec.md), [FR-003](./spec.md). Depends on T041.
- [ ] T044 [US1] Implement engine-move trigger in `crates/chess-app/src/ui/game_screen.rs`: after human move, send `Command::SetPosition` then `Command::StartSearch { config: defaults from UserSettings }`; on `Event::SearchComplete`, animate the engine's move with brief animation (200 ms) and update `Game`. Depends on T029, T038, T041.
- [ ] T045 [US1] Display engine status in `crates/chess-app/src/ui/game_screen.rs`: "Idle" / "Thinking… depth N, eval +0.42" updated from `Event::SearchProgress` per [FR-019](./spec.md) and [contracts/ui-interactions.md §2.5](./contracts/ui-interactions.md). Depends on T044.
- [ ] T046 [US1] Implement game-over detection and modal in `crates/chess-app/src/ui/game_screen.rs`: after every `Game::make_move`, check `Game::result()`; if `Some`, display modal "Game Over: Checkmate (White wins)" / "Draw — Stalemate" / "Draw — 50-Move Rule" / etc. and lock the board per [FR-003](./spec.md) and [Edge Cases](./spec.md#edge-cases). Depends on T044.
- [ ] T047 [US1] Handle mid-move shutdown gracefully in `crates/chess-app/src/main.rs`: window-close sends `Command::Stop` then `Command::Shutdown`, waits up to 200 ms for `Event::Stopped`, exits cleanly per [Edge Cases](./spec.md#edge-cases). [FR-021](./spec.md). Depends on T044.

### Validation tests for User Story 1

- [ ] T048 [P] [US1] Add `tests/corpora/rules_1000.epd` (1000+ hand-curated FIDE rules positions covering castling, en-passant, promotion, 50-move trigger, threefold trigger, insufficient-material — both positive and negative cases) per [research.md R-9](./research.md#r-9-fide-rules-test-corpus).
- [ ] T049 [P] [US1] Add `tests/rules.rs` exercising every position in `rules_1000.epd` against `chess-core`'s rules engine; assert correct legal-move set, correct termination detection, no false positives/negatives per [SC-008](./spec.md). Depends on T014, T048.
- [ ] T050 [P] [US1] Add `tests/corpora/bratko_kopec.epd` (24 positions) and `tests/corpora/win_at_chess.epd` (300 positions) — both public-domain.
- [ ] T051 [P] [US1] Add `tests/tactics.rs`: spawn engine, run each Bratko-Kopec and Win-at-Chess position with `TimeControl::PerMove(5s)`, assert ≥ 90% solved per [SC-002](./spec.md). Depends on T019, T020, T050.
- [ ] T052 [P] [US1] Add `tests/ui_smoke.rs` using egui's headless test harness: launch app with mock engine, simulate New Game, click `e2` then `e4`, assert board state updates, assert engine receives `StartSearch` per [contracts/ui-interactions.md §7](./contracts/ui-interactions.md). Depends on T044.

**Checkpoint US1**: User can launch `chess-ai.exe`, play a complete legal game vs AI, see the result, and quit cleanly. All US1 acceptance scenarios pass. SC-002 (≥ 90% tactics), SC-005 (cold start ≤ 5 s — measured manually here, formalised in T086), SC-008 (1000-position rules suite green) verified. **MVP is shippable from this checkpoint.**

---

## Phase 4: User Story 2 - Configure AI Strength and Time Control (Priority: P2)

**Goal**: User picks a strength preset (Beginner / Intermediate / Advanced / Maximum) and a time control (per-move time / fixed depth / total time per side); selections persist across sessions; AI behaviour visibly changes.

**Independent Test**: With US1 working, open Settings, choose "Beginner", play a game, observe weaker play (loses to a known mid-level reference line per [SC-003](./spec.md)). Restart the application; confirm "Beginner" is still selected. Set per-move time to 1 s; observe AI replies in ~1 s ± 20% per [SC-004](./spec.md).

### Implementation for User Story 2

- [ ] T053 [P] [US2] Implement `crates/chess-app/src/ui/settings_screen.rs` form layout: section headers (Engine / UI / Diagnostics) with widgets per [contracts/ui-interactions.md §3](./contracts/ui-interactions.md). Initial pass: render-only against current `UserSettings`.
- [ ] T054 [US2] Add Strength preset dropdown in `crates/chess-app/src/ui/settings_screen.rs`: `Beginner / Intermediate / Advanced / Maximum` mapped to `EngineConfig::strength` per [FR-010](./spec.md). On change, update in-memory `UserSettings` and queue debounced save. Depends on T053.
- [ ] T055 [US2] Add TimeControl picker in `crates/chess-app/src/ui/settings_screen.rs`: dropdown of common presets (`PerMove:1s / 5s / 10s / 30s / 60s`, `FixedDepth:8 / 12 / 16`, `Total:5m:3s / 10m:0s`) plus custom-input row that parses via the TimeControl grammar. [FR-010](./spec.md), [contracts/settings-file.md §3](./contracts/settings-file.md). Depends on T026, T053.
- [ ] T056 [US2] Add `max_threads` slider (range `1..=num_cpus()`), `reproducible_mode_default` checkbox, `hint_time_ms` numeric input in `crates/chess-app/src/ui/settings_screen.rs` per [contracts/ui-interactions.md §3](./contracts/ui-interactions.md). Depends on T053.
- [ ] T057 [US2] Wire settings changes to live engine: when `max_threads` or `debug_logging` changes, send `Command::SetConfig { max_threads, debug_logging }` to the engine per [contracts/engine-api.md §2](./contracts/engine-api.md). When `default_strength`/`default_time_control`/`reproducible_mode_default` changes, the next `StartSearch` will use the new config. Depends on T029, T056.
- [ ] T058 [US2] Implement debounced save in `crates/chess-app/src/settings.rs`: a `dirty: bool` flag set on any mutation; a tick driver in the egui frame loop that, every 250 ms, atomically writes if dirty per [contracts/settings-file.md §4.2](./contracts/settings-file.md). Also flush on window-close. Depends on T025.
- [ ] T059 [US2] Add Settings menu entry in `crates/chess-app/src/ui/game_screen.rs` (top menu bar) and `Ctrl+,` hotkey routing to `Screen::Settings` per [contracts/ui-interactions.md §1](./contracts/ui-interactions.md). Depends on T053.
- [ ] T060 [US2] Add "Reset to defaults" button in `crates/chess-app/src/ui/settings_screen.rs` that restores `UserSettings::default()` and saves immediately per [contracts/ui-interactions.md §3](./contracts/ui-interactions.md).

### Validation tests for User Story 2

- [ ] T061 [P] [US2] Add `tests/settings_file.rs` covering the 5 test obligations from [contracts/settings-file.md §8](./contracts/settings-file.md): round-trip, atomicity (kill mid-write), corruption recovery (`.bad-` rename), unknown-field rejection, out-of-range clamping.
- [ ] T062 [P] [US2] Add `tests/strength_differentiation.rs`: play 20 self-play games at low time control (`PerMove:200ms`) between Beginner and Maximum presets; assert Maximum wins ≥ 90% of decisive games per [SC-003](./spec.md). (Sample size reduced from spec's 100 for CI runtime; full 100-game match is a manual / nightly job.)
- [ ] T063 [P] [US2] Add `tests/time_accuracy.rs`: at each per-move setting in `[100ms, 500ms, 1s, 5s, 10s]`, run 50 moves in random midgame positions; assert ≥ 99% are within ±20% of budget and none exceed by > 50% per [SC-004](./spec.md).

**Checkpoint US2**: User can configure strength and time control; settings persist across restarts; engine respects them. SC-003, SC-004 verifiable. US1 + US2 ship together.

---

## Phase 5: User Story 3 - In-Session Move History and Undo/Redo (Priority: P2)

**Goal**: Visible move list in SAN; unlimited Undo/Redo within the session; correct restoration across special moves (castling, en passant, promotion); no on-disk persistence.

**Independent Test**: With US1 working, play 10 moves including at least one castle and one promotion; verify the move list shows correct SAN with move numbers, captures, checks, mates. Undo 3 times; verify board returns to the correct earlier position with castling rights, en-passant target, and halfmove clock fully restored. Redo 3 times; verify identical replay.

### Implementation for User Story 3

- [ ] T064 [P] [US3] Implement scrollable move-list panel in `crates/chess-app/src/ui/game_screen.rs`: pairs of (white-move, black-move) with move number prefix ("1. e4 e5"), SAN with check/mate suffix, captured-piece indicators per [FR-019](./spec.md) and [contracts/ui-interactions.md §2.4](./contracts/ui-interactions.md). The currently-displayed half-move is highlighted. Depends on T041.
- [ ] T065 [US3] Implement "click past move to navigate" in `crates/chess-app/src/ui/game_screen.rs`: clicking a move in the list shows that historical position on the board (read-only view); a "Return to live game" indicator appears; clicking the most-recent move or the indicator returns to the live position per [contracts/ui-interactions.md §2.4](./contracts/ui-interactions.md). Depends on T064.
- [ ] T066 [US3] Implement Undo / Redo buttons + `Ctrl+Z` / `Ctrl+Shift+Z` hotkeys in `crates/chess-app/src/ui/game_screen.rs`: invoke `Game::undo` / `Game::redo`; if the engine is searching, send `Command::Stop` first and wait for `SearchAborted` before applying the undo. Pop both the engine's move and the human's move on Undo so it's the human's turn again per [contracts/ui-interactions.md §2.3](./contracts/ui-interactions.md). [FR-014](./spec.md). Depends on T029, T064.
- [ ] T067 [US3] Implement branching behaviour: `Game::make_move` from a navigated past position truncates `history` to that point, clears `redo_stack`, then applies the new move per [contracts/ui-interactions.md §2.4](./contracts/ui-interactions.md) and standard chess-app semantics. Depends on T065, T066.

### Validation tests for User Story 3

- [ ] T068 [P] [US3] Add unit tests in `crates/chess-core/src/game.rs` for Undo/Redo round-trip across every special-move kind: castling (KS + QS), en-passant (white & black), promotion (Q/R/B/N), promotion-with-capture-with-check. For each, assert `position == undo(make_move(position, m))` bit-for-bit (including Zobrist) per [data-model.md cross-cutting invariant 1](./data-model.md). [User Story 3 Acceptance Scenario 1](./spec.md).
- [ ] T069 [P] [US3] Add `tests/move_list_snapshot.rs` using `insta` snapshot testing (per [research.md R-11](./research.md#r-11-test-framework)): play a curated 50-move game with several special moves and snapshot the move-list SAN output; future regressions in SAN rendering fail this test.
- [ ] T070 [P] [US3] Add `tests/repetition_detection.rs`: build a position that reaches threefold repetition via knight shuffles; assert `Game::result()` returns `Draw(ThreefoldRepetition)` exactly on the move that triggers the third occurrence per [Edge Cases](./spec.md#edge-cases). Test 50-move-rule trigger similarly.

**Checkpoint US3**: Move list, Undo, Redo, and history navigation all work; special moves round-trip cleanly; SAN rendering is stable.

---

## Phase 6: User Story 4 - Watch the AI Play Itself (AI vs AI) (Priority: P3)

**Goal**: User selects "AI vs AI", picks two strengths, watches the game play itself; can pause/resume.

**Independent Test**: With US1 working, start an AI-vs-AI game at Maximum vs Maximum; verify it runs to completion in a valid terminal state with all legal moves. Pause mid-game; verify engine stops and board is preserved. Resume; verify play continues from the same position.

### Implementation for User Story 4

- [ ] T071 [US4] Add `GameMode` field to `Game` (`HumanVsAi(Color)` / `AiVsAi { white_strength, black_strength }`) in `crates/chess-core/src/game.rs` per [data-model.md §4](./data-model.md). Update existing call sites. Depends on T014.
- [ ] T072 [US4] Add "Mode" radio group to "New Game" modal in `crates/chess-app/src/ui/game_screen.rs`: `Human vs AI (play White)` / `Human vs AI (play Black)` / `AI vs AI`. When `AI vs AI` is selected, show two strength dropdowns (one per side). Depends on T042, T053, T071.
- [ ] T073 [US4] Implement self-play loop in `crates/chess-app/src/ui/game_screen.rs`: on `GameMode::AiVsAi`, after each `Event::SearchComplete`, automatically issue the next `Command::SetPosition` + `Command::StartSearch` for the other side, until `Game::result().is_some()` per [User Story 4 Acceptance Scenario 1](./spec.md). [FR-007](./spec.md). Depends on T044, T071.
- [ ] T074 [US4] Implement Pause/Resume button in `crates/chess-app/src/ui/game_screen.rs` (visible only in `AiVsAi` mode): Pause sends `Command::Stop` and sets a `paused: bool` flag in `UiState`; Resume re-issues `StartSearch` from current position per [User Story 4 Acceptance Scenario 2](./spec.md).

### Validation tests for User Story 4

- [ ] T075 [P] [US4] Add `tests/ai_vs_ai.rs`: spawn engine, drive a full self-play loop at `PerMove:200ms` Beginner vs Beginner from the standard starting position; assert (a) every move emitted is in `legal_moves(p)`, (b) the loop terminates with `Game::result().is_some()` within 200 plies, (c) the result is one of `Checkmate(_)` or `Draw(_)` per [User Story 4 Acceptance Scenario 1](./spec.md).

**Checkpoint US4**: AI-vs-AI mode runs cleanly, pauses, and resumes.

---

## Phase 7: User Story 5 - Get Engine Hints and Position Analysis (Priority: P3)

**Goal**: User can request a hint at any time during their turn; engine returns top move + numeric eval + principal variation. No separate engine — same `chess-engine`, called with `analysis_only = true`.

**Independent Test**: With US1 working, on the user's turn, press `H` (or click Hint); verify a single legal move arrow appears on the board, a numeric eval (e.g., "+0.45") and PV display in the right panel, all within `hint_time_ms` (default 3 s). On a forced-mate-in-3 position, request analysis; verify "Mate in 3" + winning line.

### Implementation for User Story 5

- [ ] T076 [US5] Add Hint button + `H` hotkey in `crates/chess-app/src/ui/game_screen.rs`: sends `Command::StartSearch` with `EngineConfig { analysis_only: true, time_control: PerMove(hint_time_ms), .. }` per [contracts/ui-interactions.md §2.3](./contracts/ui-interactions.md). [FR-020](./spec.md). Depends on T020, T044.
- [ ] T077 [US5] Render hint result in `crates/chess-app/src/ui/board.rs` as an arrow overlay from `mv.from` to `mv.to` (semi-transparent green); auto-clear on next user click or after 5 s per [contracts/ui-interactions.md §2.3](./contracts/ui-interactions.md). [User Story 5 Acceptance Scenario 1](./spec.md). Depends on T076.
- [ ] T078 [US5] Render numeric evaluation and principal variation in the right panel of `crates/chess-app/src/ui/game_screen.rs`: format as `+0.45` / `-1.20` / `Mate in 3`; PV shown as space-separated SAN moves per [FR-020](./spec.md). Depends on T076.
- [ ] T079 [US5] Ensure `analysis_only = true` does NOT advance the game (doesn't apply the move, doesn't trigger engine's next-turn search) — verified by code review and a test in T080.

### Validation tests for User Story 5

- [ ] T080 [P] [US5] Add `tests/hint_mate.rs`: load 10 forced-mate-in-N positions (curated from Win-at-Chess subset where `bm` is a mate sequence), request analysis with `time_control = PerMove(5s)`, assert `SearchInfo.eval == Eval::Mate(N)` with the correct N and `SearchInfo.pv` contains the documented winning line per [User Story 5 Acceptance Scenario 2](./spec.md).

**Checkpoint US5**: Hints and analysis available on demand; no game state changed by analysis.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Reproducible Mode, About screen, accessibility, additional CLI flags, observability, performance benches, distribution, and final SC validation.

- [ ] T081 [P] Implement `Mode::Reproducible` logic in `crates/chess-engine/src/repro.rs`: when `EngineConfig.mode == Reproducible { seed }`, force `max_threads = 1`, seed all internal RNG (Dirichlet noise / aspiration jitter) from `seed`, replace clock-based move-ordering tiebreaks with position-hash tiebreaks, disable time-based aborts in favour of fixed-depth/fixed-nodes per [research.md R-7](./research.md#r-7-reproducible-mode-implementation). [FR-009](./spec.md). Depends on T019, T020.
- [ ] T082 [P] Implement Reproducible Mode UI: status badge (top-right, "Default" / "Reproducible") in `crates/chess-app/src/ui/game_screen.rs` and `Ctrl+R` hotkey toggle per [FR-009a](./spec.md) and [contracts/ui-interactions.md §2.3, §2.5](./contracts/ui-interactions.md). Depends on T041.
- [ ] T083 [P] Add `tests/reproducible.rs`: for each of 50 positions including Kiwipete, run two searches at `Mode::Reproducible { seed: 0xDEAD_BEEF }` with `FixedDepth(10)`; assert `SearchResult.mv` AND `SearchInfo.pv` are bit-identical across runs per [data-model.md cross-cutting invariant 4](./data-model.md). [FR-009](./spec.md).
- [ ] T084 [P] Implement `crates/chess-app/src/ui/about_screen.rs`: app name + `env!("CARGO_PKG_VERSION")`, engine name (`forked from Carp 3.0.1`), network short hash (first 8 chars of `CHESS_NETWORK_HASH` from build.rs), build commit (`VERGEN_GIT_SHA`), build timestamp, link to repository (opens default browser via `open` crate), full MIT license text scrollable region per [contracts/ui-interactions.md §4](./contracts/ui-interactions.md).
- [ ] T085 [P] Add Help menu entry → About screen in `crates/chess-app/src/ui/game_screen.rs`. Depends on T084.
- [ ] T086 [P] Implement Dark and HighContrast themes in `crates/chess-app/src/ui/theme.rs` per [research.md R-16](./research.md#r-16-localization-and-accessibility-deferred-items-from-clarification) and [contracts/ui-interactions.md §3](./contracts/ui-interactions.md). Switch is immediate on selection in Settings.
- [ ] T087 [P] Implement keyboard board navigation in `crates/chess-app/src/ui/board.rs`: `Tab` enters board focus; arrow keys move a focus-square; first `Space` selects, second `Space` confirms (or cancels if illegal) per [research.md R-16](./research.md#r-16-localization-and-accessibility-deferred-items-from-clarification) and [contracts/ui-interactions.md §5](./contracts/ui-interactions.md).
- [ ] T088 [P] Implement keyboard SAN input in `crates/chess-app/src/ui/game_screen.rs`: hidden text field that activates on `Tab`, parses input via `chess_core::san::parse_san`; on unique-legal-move match, plays it; otherwise flashes input red per [FR-017](./spec.md) and [contracts/ui-interactions.md §2.1](./contracts/ui-interactions.md). Depends on T013.
- [ ] T089 [P] Implement remaining CLI flags in `crates/chess-app/src/cli.rs`: `--reset-settings` (rename existing settings.toml with `.bak-<ts>` suffix), `--portable` (use exe-dir as data root), `--log-debug` (force `[diagnostics] debug_logging = true` for this run only) per [contracts/cli-flags.md](./contracts/cli-flags.md). Depends on T027.
- [ ] T090 [P] Implement `--self-test` in `crates/chess-app/src/self_test.rs`: runs (a) engine init, (b) sub-suite of `tests/rules.rs` (~50 critical positions), (c) two `Mode::Reproducible` searches with identical-output check, prints summary, exits 0 / 1 per [contracts/cli-flags.md](./contracts/cli-flags.md).
- [ ] T091 [P] Add `tests/cli.rs` covering all 6 test obligations from [contracts/cli-flags.md "Test contract"](./contracts/cli-flags.md): `--version` exit-0 + version regex, `--help` exit-0, `--self-test` exit-0 ≤ 60 s, `--version --help` exit-2, `--bogus` exit-2, `--reset-settings` renames existing file. Use `assert_cmd` crate for subprocess assertions.
- [ ] T092 [P] Implement `tracing` setup in `crates/chess-app/src/main.rs`: default `WARN` to stderr; if `--log-debug` or `[diagnostics] debug_logging = true`, also write to `%LOCALAPPDATA%\chess-ai\logs\chess-ai-<date>.log` with daily rotation (max 7 days kept) per [research.md R-14](./research.md#r-14-logging-and-diagnostics).
- [ ] T093 [P] Add `benches/movegen.rs` (criterion): perft 6 from initial position; target ≥ 100M nps per [research.md R-10](./research.md#r-10-performance-benchmarks).
- [ ] T094 [P] Add `benches/nnue.rs` (criterion): single-position eval throughput; target ≥ 5M evals/sec/thread per [research.md R-10](./research.md#r-10-performance-benchmarks).
- [ ] T095 [P] Add `benches/search.rs` (criterion): full search at depth 12 from Kiwipete on 8 threads; target ≥ 1.5M nps per [research.md R-10](./research.md#r-10-performance-benchmarks).
- [ ] T096 Add `tests/no_io.rs` with feature-gated panicking shims for `std::fs` / `std::net` (`#[cfg(feature = "no-io-audit")]`); verify `Engine::start_search` opens zero files, sockets, or system resources per [contracts/engine-api.md §3.5](./contracts/engine-api.md) and [SC-007](./spec.md).
- [ ] T097 Run static-CRT release build per [quickstart.md §2](./quickstart.md#2-build) and verify `dumpbin /dependents target\x86_64-pc-windows-msvc\release\chess-ai.exe` shows zero `VCRUNTIME140.dll` / `MSVCP140.dll` per [research.md R-12](./research.md#r-12-distribution-and-code-signing).
- [ ] T098 Manual SC-005 validation: cold-start time on a clean Windows 10/11 x86-64 VM with no network; assert ≤ 5 s to playable board on first launch and ≤ 2 s on subsequent launches per [SC-005](./spec.md). Document results in `specs/001-chess-ai-rewrite/sc-validation.md`.
- [ ] T099 Manual SC-006 validation: monitor RAM/CPU at idle (≤ 500 MB / 0% CPU) and during search at default strength (≤ 75% CPU cores) over a 10-minute session per [SC-006](./spec.md). Document.
- [ ] T100 Manual SC-007 validation: install on a never-networked machine, complete a full Human-vs-AI game, run `procmon` filesystem audit and `wireshark` packet capture; assert zero outbound network attempts and zero disk writes outside `%APPDATA%\chess-ai\settings.toml` per [SC-007](./spec.md). Document.
- [ ] T101 SC-009 nightly stability test: GitHub Actions scheduled job runs 24 hours of back-to-back AI-vs-AI games at maximum strength on a Windows runner; assert no crash, no illegal-move output, < 10% memory growth per [SC-009](./spec.md). Document.
- [ ] T102 SC-001 strength validation: 100-game match between `chess-ai.exe` at Maximum strength (60 s/move) and a publicly available 2500-Elo reference engine via cutechess-cli; assert ≥ 95% win/draw rate per [SC-001](./spec.md). Document. (One-time milestone test; not in CI due to runtime.)
- [ ] T103 [P] Update workspace `README.md` (replace legacy README) with v1 chess-ai overview: features, requirements (Windows x86-64, no network), build/run instructions linking to `specs/001-chess-ai-rewrite/quickstart.md`, license summary.
- [ ] T104 Run `quickstart.md` end-to-end on a clean Windows checkout: prerequisites install, clone, build (debug + release + static-CRT), run, all 8 integration test invocations green, all 3 benchmarks meet targets per [quickstart.md §10](./quickstart.md). Document any deviations.
- [ ] T105 Code-sign the static-CRT release `.exe` with EV/OV certificate via SignTool; publish SHA-256 alongside on GitHub Releases per [research.md R-12](./research.md#r-12-distribution-and-code-signing). (Release-time only; certificate procurement is out-of-scope for the engineering task list.)

---

## Phase 9: Post-analysis Remediation Tasks

**Purpose**: Tasks added after `/speckit.analyze` (2026-04-26) to close coverage gaps identified in the analysis report (findings F3, F5, F9). Distributed across stories — they are listed as a single phase only for traceability to the analysis run.

- [ ] T106 [US1] Implement "Swap Sides" action in `crates/chess-app/src/ui/game_screen.rs` (button + menu entry): cancels any in-flight engine search, flips `Game.user_color` and the board orientation in `UiState`, and immediately triggers a new engine search if it is now the AI's turn. Available at any point during a Human-vs-AI game per [FR-012](./spec.md). Closes F5. Depends on T044, T071.
- [ ] T107 [P] [US3] Add `tests/long_game.rs`: synthetically build a 300-ply game (e.g., shuffling-knight loop with a perturbation move every ~80 plies to avoid threefold), then iterate Undo to ply 0 and Redo back to ply 300; assert no panic, no allocator growth between iterations 2 and 5 (within 5%), and Zobrist round-trip equality at every checkpoint per [Edge Cases](./spec.md#edge-cases) ("Long games of 300+ plies remain stable in memory and history navigation"). Closes F9.
- [ ] T108 Conduct SC-010 unmoderated usability test: 10 chess-literate participants, 5 tasks (start a new game and play 5 moves; change AI strength; undo and redo a move; request a hint; start an AI-vs-AI game), 2 minutes per task, ≥ 9/10 completion target per [SC-010](./spec.md). Document protocol, raw observations, and pass/fail per task in `specs/001-chess-ai-rewrite/sc-validation.md`. Closes F3. (Manual / one-time; not in CI.)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 (T001–T008). Internal dependencies form three parallel sub-tracks (`chess-core`, `chess-engine`, `chess-app`) that converge at T029. **BLOCKS all user stories.**
- **Phases 3–7 (User Stories US1–US5)**: All depend on Phase 2 completion (specifically T029 + T030–T032 green).
  - **US1 (Phase 3)** has no story dependencies; it is the MVP.
  - **US2 (Phase 4)**, **US3 (Phase 5)**, **US4 (Phase 6)**, **US5 (Phase 7)** all *integrate with* US1's UI surface (game_screen.rs) but each is independently testable per its acceptance criteria. Practically, US1 should be at least at "playable" stage (T044 done) before US2–US5 begin if a single developer is working.
- **Phase 8 (Polish)**: Mostly independent of stories; some tasks (T082, T086, T089) consume hooks added by US1/US2.

### User Story Dependencies

- **US1 (P1)**: After Phase 2.
- **US2 (P2)**: After Phase 2; integrates with US1's `game_screen.rs` (top menu) — minor file-level dependency on T041.
- **US3 (P2)**: After Phase 2; integrates with US1's `game_screen.rs` — minor file-level dependency on T041, T044.
- **US4 (P3)**: After Phase 2; integrates with US1's `game_screen.rs` and `Game` — minor file-level dependency on T041, T042, T044.
- **US5 (P3)**: After Phase 2; integrates with US1's `board.rs` and `game_screen.rs` — minor file-level dependency on T041, T044.

### Within Each User Story

- Implementation tasks proceed in numeric order (lower IDs are prerequisites of higher IDs).
- Validation tests at the end of each story can run in parallel with each other (all marked `[P]`).
- Each story is complete when all its tasks pass, including its validation tests.

### Parallel Opportunities

- **Setup (Phase 1)**: T003, T004, T005, T006, T007, T008 are all `[P]` — six tasks can run concurrently after T002.
- **Foundational (Phase 2)**:
  - **chess-core sub-track [P]**: T009, T010, T012, T013 in parallel; then T011 (depends on T009/T010); then T014 (depends on T011/T012/T013); then T015.
  - **chess-engine sub-track**: T016 → T017 → T018 → T019/T020 → T021 (mostly sequential because of in-place refactor).
  - **chess-app sub-track [P]**: T022, T023 in parallel; T024 → T025 → T026; T027 in parallel; T028 (joins all three sub-tracks); T029.
  - **Validation [P]**: T030, T031, T032 fully parallel.
- **Within each user story**: validation-test tasks marked `[P]` (e.g., T048–T052, T061–T063, T068–T070, T075, T080) can all run in parallel.
- **Across user stories (Phases 3–7)**: with multiple developers, all five stories can proceed in parallel after Phase 2; integration into shared files (`game_screen.rs`) is the only serialization point.
- **Polish (Phase 8)**: T081–T096 are mostly `[P]` (15 of 16). Only T097–T105 are sequential validation/release tasks.

---

## Parallel Examples

### Phase 1 — kick off setup in parallel

```text
After T001 + T002 complete, launch concurrently:
- T003: Create rust-toolchain.toml
- T004: Create .cargo/config.toml
- T005: Update .gitignore
- T006: Create three crate skeletons
- T007: Add rustfmt + clippy config
- T008: Create CI workflow
```

### Phase 2 — chess-core sub-track in parallel

```text
After T002 complete, launch concurrently (chess-core only):
- T009: Implement Position
- T010: Implement Move + MoveRecord
- T012: Implement rules.rs (threefold/50-move/insufficient material)
- T013: Implement san.rs
Then T011 (movegen, depends on T009/T010), then T014 (Game, depends on T011/T012/T013), then T015 (lib.rs re-exports).
```

### Phase 3 — User Story 1 validation tests in parallel

```text
After T044 + T046 (US1 implementation core), launch concurrently:
- T048: Compose tests/corpora/rules_1000.epd
- T049: tests/rules.rs (depends on T048)
- T050: Compose Bratko-Kopec + Win-at-Chess corpora
- T051: tests/tactics.rs (depends on T050)
- T052: tests/ui_smoke.rs
```

### Phase 8 — Polish in parallel

```text
After all of Phases 3–7 complete, launch concurrently:
- T081: Reproducible Mode engine logic
- T084: About screen
- T086: Dark + HighContrast themes
- T087: Keyboard board navigation
- T088: Keyboard SAN entry
- T089: Remaining CLI flags
- T092: tracing logging
- T093/T094/T095: Three benchmarks
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T008) — ~half a day.
2. Complete Phase 2: Foundational (T009–T032) — the engine bring-up and core wiring; this is the largest single phase. Multi-day effort.
3. Complete Phase 3: User Story 1 (T033–T052) — core gameplay loop.
4. **STOP & VALIDATE**: Run `tests/perft`, `tests/rules`, `tests/tactics`, `tests/ui_smoke`, `tests/engine_legal`, `tests/cancel_latency`. Manual smoke: launch `chess-ai.exe`, play a full game, verify game-over modal.
5. **MVP shippable** at this checkpoint — a single-binary chess app with world-class AI strength, default settings, no save/load, no AI-vs-AI, no hints.

### Incremental Delivery

1. Setup + Foundational → engine working, all stories unblocked.
2. US1 → Test → Demo (MVP — useful, shippable).
3. US2 → Test → Demo (configurable strength + persistence).
4. US3 → Test → Demo (move list + undo/redo).
5. US4 → Test → Demo (AI-vs-AI watching).
6. US5 → Test → Demo (hints + analysis).
7. Polish → SC validation → release candidate.

Each story adds value without breaking the previous; each is independently shippable from US1 onward.

### Parallel Team Strategy

With 3+ developers post-Foundational:

1. Team completes Setup + Foundational together (Phase 1 + Phase 2). The three Phase-2 sub-tracks (`chess-core`, `chess-engine`, `chess-app`) split naturally across three engineers.
2. Once T029 + T030–T032 are green:
   - Engineer A: US1 (T033–T052) + US3 (T064–T070) — both touch `game_screen.rs`, sensibly held by one person.
   - Engineer B: US2 (T053–T063) — focused on settings_screen.rs and persistence.
   - Engineer C: US4 (T071–T075) + US5 (T076–T080) — both are engine-integration features.
3. Polish phase shared across the team (highly parallel — most tasks `[P]`).

---

## Notes

- `[P]` tasks = different files, no incomplete-task dependencies in the same phase.
- `[Story]` label maps task to its user story for traceability across spec/plan/tasks.
- Each user story is independently completable and testable.
- Tests are not TDD-driven (the spec did not request TDD), but they ARE required because [Success Criteria](./spec.md#success-criteria-mandatory), [contracts](./contracts/), and [data-model invariants](./data-model.md) all reference specific test files. Test tasks are placed at the end of each story phase.
- Commit cadence: after each task or each logical group (e.g., after a sub-track completes in Phase 2; after each Checkpoint).
- Stop at any Checkpoint to validate the story independently before moving to the next.
- Avoid: vague tasks, parallel writes to the same file (e.g., `game_screen.rs` is touched by US1/US2/US3/US4/US5 — sequence carefully or split file logically), cross-story dependencies that would break independence.
