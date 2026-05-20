# Feature Specification: Rust-to-C++26 Chess Application Rewrite

**Feature Branch**: `002-rust-cpp26-rewrite`
**Created**: 2026-05-20
**Status**: Draft
**Input**: User description: "Convert the RUST implementation of CHESS into a C++26 code"

## Context & Rationale

The project already delivers a constitution-compliant chess product as a single Windows desktop executable written in Rust (`crates/chess-app`, `chess-core`, `chess-engine`, and related crates). That implementation covers full FIDE rules, a world-class integrated NNUE engine, native UI, strength/time controls, in-session undo/redo, hints/analysis, AI-vs-AI, and offline operation — as defined in `specs/001-chess-ai-rewrite/spec.md`.

This feature specifies a **language migration**: reimplement the same product capabilities in **C++26** while preserving user-visible behavior, constitutional constraints, and measurable quality targets. The existing Rust codebase becomes **legacy reference material** for behavior, test corpora, and acceptance criteria; it is not extended in parallel once the C++26 product reaches parity.

The migration MUST NOT reintroduce architectural violations the constitution eliminated (web server, browser UI, multiple engines, cloud dependencies, or distributed components). The outcome remains one local executable with one integrated engine and one native UI process.

## Clarifications

### Session 2026-05-20

- Q: What C++26 build toolchain standard should this migration require? → A: Block until `/std:c++26` is officially supported — no migration work starts until a compiler ships the final flag.
- Q: Which compiler's official `/std:c++26` flag must be available before implementation starts? → A: MSVC + Clang both required — both must ship official C++26 flags before any source is written.
- Q: While the toolchain gate is active, what work is permitted in the repository? → A: Preview/scaffold allowed — C++ source permitted under `/std:c++latest` or `-std=c++2c` only.
- Q: After the toolchain gate clears, which compiler produces the shipped Windows `.exe`? → A: Dual release — both compilers must produce passing release builds; either may ship.
- Q: What build orchestration is required for dual MSVC + Clang C++26 builds? → A: CMake + Ninja required — single CMake project; separate MSVC and Clang release configurations.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Play the Same Game Experience After Migration (Priority: P1)

A chess player who used the Rust version launches the new C++26 executable and plays a complete legal game against the built-in AI with the same core interactions: board display, legal-move validation, AI responses within the configured time budget, and correct game termination.

**Why this priority**: If migrated behavior diverges from the established product, the rewrite delivers no user value regardless of language choice. This story validates end-to-end parity for the primary use case.

**Independent Test**: On a machine with no network access, launch the C++26 executable, start Human vs AI as White, play 30+ legal moves, receive legal AI replies within the configured time budget, and finish a forced mate-in-2 test position with the correct result announcement.

**Acceptance Scenarios**:

1. **Given** the C++26 executable is launched offline, **When** the user starts a new Human vs AI game and makes a legal opening move, **Then** the board updates, the AI responds with a legal move within the configured time budget, and game state matches FIDE rules.
2. **Given** an in-progress game, **When** the user attempts an illegal move, **Then** the move is rejected with clear feedback and the position is unchanged — matching the behavior defined for the Rust product.
3. **Given** a position one move from checkmate, **When** the mating move is played, **Then** checkmate is detected, further moves are locked, and the winner is announced.
4. **Given** a known drawn position, **When** the triggering move is played, **Then** the correct draw type is announced.

---

### User Story 2 - Retain Strength, Time Control, and Settings Behavior (Priority: P1)

The user configures AI strength presets, time controls, and reproducible search mode exactly as in the current product. Settings persist across sessions, and strength differentiation remains observable between presets.

**Why this priority**: Engine strength and configurability are core product differentiators. A language migration that weakens the engine or drops settings parity would fail the migration goal.

**Independent Test**: Set "Beginner" strength, play one game, switch to "Maximum", play another, observe measurably stronger play at Maximum; restart the application and confirm persisted settings.

**Acceptance Scenarios**:

1. **Given** the user selects a Beginner preset, **When** they play against Maximum at the same time control in repeated trials, **Then** Maximum wins the clear majority of decisive games (per SC-003).
2. **Given** a per-move time of 1 second, **When** the AI moves, **Then** move delivery stays within ±20% of the budget in at least 99% of moves over a 1000-move sample.
3. **Given** Reproducible Mode with fixed inputs, **When** the same search is run twice on the same machine, **Then** move choice and principal variation are identical across runs.
4. **Given** changed settings, **When** the user closes and reopens the application, **Then** previously selected strength and time-control settings are still active.

---

### User Story 3 - Preserve In-Session History and Analysis Features (Priority: P2)

During a game, the user views the move list in standard algebraic notation, undoes and redoes moves (including special moves), requests hints, and receives position analysis from the same single engine — with no game files written to disk.

**Why this priority**: These features are part of the current shipped experience and training value. They can be validated independently once core gameplay works.

**Independent Test**: Play 10 moves, undo three times with correct state restoration, redo two moves, request a hint with evaluation and principal variation, and confirm no game-state files appear on disk beyond the settings file.

**Acceptance Scenarios**:

1. **Given** a game with castling, en passant, or promotion, **When** the user undoes the last move, **Then** board state, rights, clocks, and move history are fully restored.
2. **Given** undone moves, **When** the user chooses Redo, **Then** moves reapply in order with identical state.
3. **Given** an in-progress game, **When** the user requests a hint, **Then** the engine returns a legal suggested move, numeric evaluation, and principal variation within the analysis time budget.
4. **Given** the application is closed mid-game, **When** it is reopened, **Then** a new game starts from the standard position; no prior game is restored.

---

### User Story 4 - Run AI-vs-AI and Stability Workloads (Priority: P3)

The user starts AI-vs-AI self-play at configurable strengths, can pause and resume, and the application remains stable under long-running automated play — matching current product capabilities required by the constitution.

**Why this priority**: Self-play is constitutionally required and stress-tests engine integration, threading, and UI update loops in the new language runtime.

**Independent Test**: Start AI vs AI at maximum strength, let it run to a legal terminal state, then run continuous back-to-back games for 24 hours without crash, illegal moves, or runaway memory growth.

**Acceptance Scenarios**:

1. **Given** AI vs AI at maximum strength, **When** the game completes, **Then** every move is legal and the result is a valid terminal state with full move history available.
2. **Given** a running self-play game, **When** the user pauses, **Then** searching stops and the position is preserved until resume.
3. **Given** mismatched strength presets over many games, **When** results are aggregated, **Then** the stronger preset wins the clear majority of decisive games.

---

### Edge Cases

- **Parity regressions**: Any user-visible behavior that existed in the Rust product and is in scope MUST be preserved unless explicitly excluded below; differences MUST be documented and justified.
- **Preview scaffold during toolchain gate**: C++ source written under `/std:c++latest` or `-std=c++2c` MUST NOT be treated as release-ready; it MUST be rebuilt and revalidated under official C++26 flags before shipping.
- **Illegal engine output**: The engine MUST never return an illegal move or no move when legal moves exist, including on time budget expiry.
- **Special moves and draw rules**: Castling rights, en passant timing, promotion choices, threefold repetition, 50-move rule, and insufficient material MUST behave identically to the Rust reference test suites.
- **Mid-search shutdown**: Closing during AI thinking MUST shut down cleanly within 200 ms with no orphan processes.
- **Long games**: Games of 300+ plies remain stable for navigation, undo/redo, and move-list display.
- **Resource limits**: Idle and searching resource usage MUST remain within the success-criteria envelopes defined for the Rust product.
- **License continuity**: Embedded engine assets and distribution obligations from the Rust product (including GPL-3.0 obligations where applicable) MUST remain compliant in the C++26 deliverable.

## Requirements *(mandatory)*

### Functional Requirements

**Migration Scope**

- **FR-001**: System MUST reimplement the chess product currently delivered by the Rust workspace (`chess-app`, `chess-core`, `chess-engine`, and integrated engine crates) as a C++26 codebase that produces a single native Windows x86-64 executable. **Production migration work MUST NOT begin until both MSVC and Clang expose official `/std:c++26` (or each vendor's equivalent finalized C++26 standard flag) on the target Windows x86-64 build host.** During the toolchain gate, preview/scaffold C++ source is permitted **only** under draft modes (`/std:c++latest` for MSVC, `-std=c++2c` for Clang); such code is non-shipping, must be clearly marked as preview, and MUST be rebased or rewritten under official C++26 flags before release.
- **FR-001a**: Before production implementation starts, the project MUST document verified toolchain versions for **both MSVC and Clang**, each with its official C++26 standard flag, plus minimal compile-and-link smoke tests proving official C++26 mode is active for both toolchains on Windows x86-64. Preview/scaffold builds under draft modes do NOT satisfy this gate.
- **FR-002**: System MUST preserve all in-scope user-facing capabilities from `specs/001-chess-ai-rewrite/spec.md` unless explicitly listed as out of scope in this specification's Assumptions section.
- **FR-003**: System MUST treat the Rust implementation and its automated test corpora as the authoritative behavioral reference for parity validation during migration.
- **FR-004**: System MUST NOT reintroduce legacy distributed architecture elements (web server, browser UI, MCP server, cloud APIs, multiple engines, or runtime network dependency).

**Game and Rules**

- **FR-005**: System MUST implement complete FIDE chess rules with the same coverage as the Rust product: piece movement, captures, check, checkmate, stalemate, castling, en passant, promotion, 50-move rule, threefold repetition, and insufficient-material draws.
- **FR-006**: System MUST validate user moves and reject illegal moves with clear, human-readable feedback.
- **FR-007**: System MUST detect and announce termination on the move that triggers mate, stalemate, or the applicable draw condition, or on resignation.

**Engine**

- **FR-008**: System MUST contain exactly one chess engine, source-integrated and statically linked into the same executable as the UI — no subprocess bridge, no engine selector, no fallback engine.
- **FR-009**: The migrated engine MUST meet the same strength, tactics, and reproducibility targets as the Rust product (see Success Criteria SC-001 through SC-004 and SC-008).
- **FR-010**: The engine MUST support Human vs AI and AI vs AI modes.
- **FR-011**: The engine MUST always return a legal move when asked, including when the time budget expires.
- **FR-012**: System MUST support user-selectable Reproducible Mode producing bit-identical search output for identical inputs on the same machine, with Default mode remaining multi-threaded for maximum strength.

**Configuration and Session Behavior**

- **FR-013**: System MUST provide the same strength presets and time-control options as the Rust product (per-move time, fixed depth, total time per side).
- **FR-014**: System MUST persist user settings locally between sessions; game state MUST NOT be persisted to disk.
- **FR-015**: System MUST support new game, resign, side switching, unlimited in-session undo/redo, and a complete in-memory move list in standard algebraic notation.

**UI and Analysis**

- **FR-016**: System MUST provide a native desktop graphical chessboard (no browser or embedded web view as the primary surface).
- **FR-017**: System MUST support mouse-based move entry (click-click and drag-and-drop) and SHOULD support algebraic keyboard entry.
- **FR-018**: System MUST visually indicate side to move, last move, legal targets for a selected piece, check, and game-end state.
- **FR-019**: System MUST provide on-demand hints and position analysis using the same single engine, returning best move, numeric evaluation, and principal variation.

**Distribution and Offline Operation**

- **FR-020**: The application MUST run fully offline at runtime with zero outbound network attempts under default settings.
- **FR-021**: All runtime engine assets MUST ship with the application (embedded or local files); none MAY be fetched at runtime.
- **FR-022**: The product MUST ship as a single statically linked native Windows x86-64 executable runnable without installing an interpreter, JVM, or separate engine binary.
- **FR-022a**: After the toolchain gate clears, **both MSVC and Clang** MUST each produce a passing release build of the application under official C++26 flags on Windows x86-64. Either build may be selected as the shipped `.exe` artifact for a given release, but both MUST remain green in CI before release.
- **FR-025**: The C++26 codebase MUST use **CMake** as the single canonical build definition with **Ninja** as the required generator for automated builds. Separate release configurations MUST exist for MSVC and Clang under official C++26 flags.

**Verification**

- **FR-023**: Migration MUST include an automated parity test suite demonstrating equivalence with Rust reference tests for rules, move generation, engine legality, time accuracy, reproducibility, and core UI workflows.
- **FR-024**: The Rust codebase MUST be moved to a clearly labeled legacy/archive area once C++26 parity is achieved; active development MUST occur in the C++26 tree thereafter.

### Key Entities

- **Game**: In-memory single active game with position, history, clocks, rights, and optional result; discarded on exit.
- **Position**: Full board state including side to move, castling rights, en passant target, and move counters.
- **Move**: Legal action with origin, destination, promotion, and special-move flags sufficient for apply/undo.
- **Engine Configuration**: Strength preset, time control, reproducibility mode, thread limits, analysis budget.
- **User Settings**: Persisted preferences (defaults for engine/UI); the only routine durable user data besides optional debug logs.
- **Parity Test Case**: A documented scenario comparing Rust reference output to C++26 output for rules, search, or UI behavior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 — Strength parity**: At maximum strength on a modern 8-core CPU at 60 seconds per move, the C++26 engine wins or draws at least 95% of games against a 2500-Elo reference engine over a 100-game match — matching the Rust product target.
- **SC-002 — Tactics parity**: At 5 seconds per position, the engine solves at least 90% of a recognized tactical test suite (≥ 200 positions) with zero illegal-move outputs.
- **SC-003 — Strength differentiation**: In 100 self-play games between Beginner and Maximum presets at the same time control, Maximum wins at least 90% of decisive games.
- **SC-004 — Time accuracy**: For per-move budgets from 100 ms to 60 s, at least 99% of moves complete within ±20% of the configured budget; none exceed budget by more than 50%.
- **SC-005 — Startup parity**: First launch reaches a playable board in under 5 seconds; subsequent launches in under 2 seconds on typical home Windows x86-64 hardware without network access.
- **SC-006 — Resource parity**: Idle RAM ≤ 500 MB and 0% sustained CPU; searching uses no more than 75% of available CPU cores by default unless user-configured otherwise.
- **SC-007 — Offline integrity**: With default settings and no prior network access, a full Human-vs-AI session including hints and AI-vs-AI completes with zero outbound network attempts and disk writes limited to the user-settings file (verified by packet capture and filesystem audit).
- **SC-008 — Rules parity**: Passes 100% of the comprehensive FIDE rules test suite used by the Rust product (≥ 1000 positions) with zero false positives or negatives.
- **SC-009 — Stability parity**: A 24-hour continuous AI-vs-AI workload completes without crash, illegal moves, or more than 10% steady-state memory growth.
- **SC-010 — Behavioral regression budget**: No more than **5** intentional, documented user-visible behavior changes versus the Rust product; any change MUST include user-facing release notes and a parity-test update.
- **SC-011 — Migration completion**: At least **95%** of automated Rust reference tests have equivalent passing C++26 tests before the Rust tree is archived for active development.
- **SC-012 — Toolchain gate**: Official C++26 standard mode is verified before production C++26 source ships — documented smoke builds using **both** MSVC and Clang finalized `/std:c++26` (or equivalent) flags succeed on the designated Windows x86-64 build host; until then, migration status remains **Blocked — Awaiting Toolchain** (preview/scaffold under draft modes excepted per FR-001).
- **SC-013 — Dual release builds**: After the gate clears, CI produces passing release builds from **both** MSVC and Clang on every release candidate; no release ships unless both are green.
- **SC-014 — Build orchestration**: A single CMake + Ninja workflow builds both compiler configurations on the designated Windows x86-64 host without manual per-compiler project duplication.

## Assumptions

- **Target language is C++26.** The entire application — UI, rules/movegen layer, engine integration, and executable packaging — is implemented in C++26 and compiled to a single native Windows x86-64 binary. Rust remains only as legacy reference until parity is proven.
- **Toolchain gate (2026-05-20):** Production implementation is **blocked** until **both MSVC and Clang** ship official C++26 standard flags. During the gate, **preview/scaffold C++ source is permitted** under draft modes only: MSVC `/std:c++latest` and Clang `-std=c++2c`. Preview code is experimental, non-shipping, and must be migrated to official C++26 flags before release. Spec/plan/contracts and toolchain readiness work (install scripts, smoke-test harnesses, CI stubs) may also continue during the gate.
- **Dual release builds (2026-05-20):** After the gate clears, **both MSVC and Clang** must produce passing release builds under official C++26 flags. Either compiler may supply the shipped `.exe` for a given release, but both builds must pass CI before release.
- **Build system (2026-05-20):** **CMake + Ninja** is required. One CMake project drives both MSVC and Clang release configurations. CMake and Ninja must be available on the Windows x86-64 build host (install during toolchain-readiness phase if not already present).
- **Platform scope unchanged:** Windows x86-64 only for v1, matching the current product. Other platforms are deferred.
- **Constitution compliance is mandatory.** Single executable, single engine, local-first operation, native UI, and minimalism constraints from `.specify/memory/constitution.md` apply unchanged.
- **Feature scope matches the Rust product.** Out-of-scope items from the Rust rewrite remain out of scope: game save/load, web UI, cloud features, MCP integration, multi-engine selection, end-user training, custom FEN setup, and non-Windows platforms.
- **Engine approach:** The migrated product retains one integrated top-tier NNUE-based engine with build-time embedded weights, comparable strength to the Rust implementation. Whether the C++26 engine is a port of the existing integrated engine or a new C++ engine with equivalent strength is a planning-phase decision; strength and legality targets are not negotiable.
- **UI framework selection** (Qt, Win32, or other native C++ toolkit) is deferred to planning, constrained to native desktop rendering without a browser layer.
- **Rust archive timing:** Rust sources are archived only after SC-011 is met; until then, both codebases may coexist in the repository for comparison testing.
- **Existing test corpora** (`perft`, tactical suites, rules EPD files, reproducibility tests) are reused or translated for C++26 validation rather than reinvented.
- **Distribution licensing** inherited from the Rust engine choice (including GPL-3.0 where applicable) carries forward to the C++26 executable.
