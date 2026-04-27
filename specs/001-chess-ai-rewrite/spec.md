# Feature Specification: Chess AI Rewrite — Single-Executable, Best-in-Class Engine

**Feature Branch**: `001-chess-ai-rewrite`
**Created**: 2026-04-26
**Status**: Draft
**Input**: User description: "examine the code in the CHESS workspace; examine the documents in the DOCS folder; examine everything in the context of the CHESS game; re-write and create a new CHESS application with the best Chess AI engine that can be used possibly"

## Context & Rationale

The current chess workspace has grown into a sprawling distributed system: a Spring Boot web server, a browser-based UI, twelve loosely-coupled AI engines (AlphaZero, Leela, AlphaFold3, A3C, MCTS, Negamax, Q-Learning, Deep Learning, CNN, DQN, Genetic Algorithm, OpenAI), an MCP/JSON-RPC server, a DB2 migration subsystem, AWS/Azure/Helm/Terraform deployment artifacts, a TypeScript frontend, and extensive cross-engine training infrastructure. The bulk of the source documentation (the `docs/` folder, ~70 design notes) describes refinements and "improvements" layered on top of this stack.

The project Constitution (`.specify/memory/constitution.md` v1.0.0) mandates a fundamental simplification:

- The product MUST run as a **single local executable**.
- It MUST contain **exactly one chess engine** — the strongest practical choice — and no fallbacks, ensembles, or plugins.
- It MUST have **no frontend/backend split**, no web server, no browser UI, no cloud APIs, and no runtime network dependency.
- It MUST be a **thick-client native UI** directly coupled to the engine inside the same process.

This specification therefore describes a **clean rewrite** that delivers the strongest possible local chess experience under those constraints. The existing workspace is treated as legacy reference material; nothing from the current `src/`, `frontend/`, `infra/`, or `mcp/` trees is carried forward except for general domain knowledge (FIDE rules already validated, opening-book formats, lessons learned).

## Clarifications

### Session 2026-04-26

- Q: Engine integration approach (bundled binary via UCI vs source-compiled vs custom-from-scratch vs Lc0) → A: Source-compiled into the application binary; implementation language is **Rust**. The chess engine is built (or ported) in Rust and statically linked into the same executable as the UI — one true monolithic binary, no subprocess, no UCI stdin/stdout bridge, no separate engine artifact to ship.
- Q: Determinism mode at maximum strength (single-threaded reproducible default vs multi-threaded fastest default) → A: **Multi-threaded search is the default** (so SC-001 / SC-002 strength targets are met on multi-core CPUs); a user-selectable **Reproducible Mode** toggle drops the engine to single-thread + fixed-seed deterministic search for testing, regression analysis, and Constitution Principle IV compliance. Reproducibility is *available on demand*, not the default.
- Q: Game persistence (save/load to disk) → A: **Out of scope for v1.** The application is a play-only tool; no save, no load, no game library, no portable export format, no game files of any kind. **In-session Undo/Redo and the move-list display are retained** because they are interaction features needed during a single game, not persistence features. User Story 3, FR-013, the Game File entity, and the related edge case are removed.
- Q: End-user training of the AI → A: **No end-user training and no project-scope training during this rewrite.** The engine ships fully trained. If a neural-network evaluation (NNUE) is used, the network weights are produced once by upstream engine authors / the chess community under permissive licenses and **embedded into the application binary at our build time** as a static byte slice. The shipped product performs **zero** weight updates, weight downloads, or training of any kind at runtime, install time, or first launch. This is consistent with FR-021 (no runtime network) and FR-022 (all engine assets ship in the binary).
- Q: Evaluation function (classical handcrafted vs NNUE pre-trained vs hybrid vs train-our-own) → A: **NNUE evaluation with a public, GPL-3.0-compatible, pre-trained network embedded into the binary at build time.** Strength target: 3400–3600 Elo. Search: multi-threaded alpha-beta + iterative deepening + transposition table + SMP. **Amended 2026-04-26**: the original answer required permissive licensing (MIT/Apache-2.0/CC0) to avoid GPL infection. After verifying that the selected upstream engine (Carp 3.0.1) is licensed under GPL-3.0 — not MIT as initially asserted in research.md R-3 — the project explicitly accepts GPL-3.0 for the entire `chess-app` executable. Suitable candidates include clean-room Rust NNUE engines such as Carp (GPL-3.0), Velvet (GPL-3.0), Akimbo (MIT), and Viridithas (MIT); Carp was selected. Selecting the specific network and engine codebase remains a planning-phase decision.
- Q: Target platforms for v1 → A: **Windows x86-64 only.** Single-platform v1 release: one Rust build target (`x86_64-pc-windows-msvc` or `x86_64-pc-windows-gnu`), one CI runner, one signed `.exe` artifact, one test environment. macOS (Intel and Apple Silicon), Linux (x86-64 and ARM), and Windows ARM are explicitly out of scope for v1 and deferred to a possible v2. SC-005, SC-007, and SC-009 are evaluated only on Windows x86-64.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Play a Complete Game Against a World-Class AI Opponent (Priority: P1)

A chess player launches the application as a single desktop executable, sees a chessboard, and plays a complete legal game (white or black) against the built-in AI. The AI plays at world-class strength on a normal home computer and responds within a configured time budget. The game ends correctly on checkmate, stalemate, draw by 50-move/threefold/insufficient material, or resignation, and the result is displayed.

**Why this priority**: This is the core product. Without it, the application has no value. It alone constitutes a viable MVP and exercises every critical subsystem: launch, UI, rules, engine, time management, and end-of-game detection.

**Independent Test**: A user installs and launches the executable on a fresh machine **with no internet connection**. They can start a new game, make 30+ legal moves, the AI responds to every move with a legal move within the configured time budget, and the game terminates with the correct result on a forced mate-in-2 setup.

**Acceptance Scenarios**:

1. **Given** the executable is launched on a machine with no network access, **When** the user starts a new game as White and makes a legal opening move, **Then** the board updates, it becomes the AI's turn, and the AI replies with a legal move within the configured per-move time budget.
2. **Given** an in-progress game, **When** the user attempts an illegal move (e.g., moving a pinned piece, castling through check, en passant on the wrong rank), **Then** the move is rejected, the board state is unchanged, and a clear feedback message is shown.
3. **Given** a position one move from checkmate, **When** the side to move plays the mating move, **Then** the application detects checkmate, locks further moves, and announces the winner.
4. **Given** a known drawn position (stalemate, 50-move rule reached, threefold repetition, or insufficient material), **When** the triggering move is played, **Then** the application detects the draw and announces the correct draw type.
5. **Given** the application is running with no special hardware (no GPU, no internet), **When** the AI is asked for a move at the default difficulty, **Then** the move is produced within the configured time budget and the move quality is consistent with a top-tier engine (no blunders in standard tactical test suites — see SC-002).

---

### User Story 2 - Configure AI Strength and Time Control (Priority: P2)

The user adjusts how strong and how fast the AI plays — choosing a strength level (e.g., beginner through grandmaster) and/or a time control (per-move time, total time per side, fixed search depth). Settings persist across sessions.

**Why this priority**: A single uncontrollable engine is unusable for most players. Strength control turns the product from a curiosity into a teaching/training tool for players of any level, and time control makes it suitable for both casual and serious play. It can be developed and tested independently of game persistence and analysis.

**Independent Test**: With Story 1 implemented, the user opens settings, selects a "beginner" preset, plays one game and observes that the AI plays measurably weaker than at "maximum" preset (e.g., loses to a known mid-level reference line); restarts the application and confirms the chosen settings are still active.

**Acceptance Scenarios**:

1. **Given** the user selects "Beginner" strength, **When** they play a game, **Then** the AI plays measurably weaker than at "Maximum" strength on the same time budget (verifiable via SC-003).
2. **Given** the user sets a per-move time of 1 second, **When** the AI's turn arrives, **Then** the AI returns a move in approximately 1 second (within ±20%) at every move.
3. **Given** the user changes the strength setting and closes the application, **When** they reopen it, **Then** the previously selected strength is still in effect.
4. **Given** a fixed-depth search is configured, **When** the AI moves, **Then** the move time varies with position complexity but search depth is bounded as configured.

---

### User Story 3 - In-Session Move History and Undo/Redo (Priority: P2)

During a single game the user can see the full move list in standard algebraic notation, undo the last move (or several) when they want to take a move back, and redo a previously undone move. There is no save-to-disk; closing the application discards the game.

**Why this priority**: Undo and a visible move list are basic in-game interaction quality features that all chess software offers. They are independent from strength control and from analysis features, and they have high value during a single learning/training session.

**Independent Test**: The user plays 10 moves, looks at the move list and confirms every move is correctly notated, then chooses Undo three times and verifies the board returns to the correct earlier position with consistent move history (including correct restoration of castling rights, en-passant target, and half-move clock across special moves).

**Acceptance Scenarios**:

1. **Given** the user has just played a move (including a special move: castling, en passant, or promotion), **When** they choose Undo, **Then** the previous position is fully restored, including all rights and counters, and the AI does not auto-respond.
2. **Given** the user has undone one or more moves, **When** they choose Redo, **Then** the move(s) are reapplied in order with all state restored exactly.
3. **Given** an in-progress game, **When** the user views the move list, **Then** moves are shown in standard algebraic notation with move numbers, captures, checks, and mates correctly indicated.
4. **Given** the user closes the application during a game, **When** they reopen it, **Then** the application starts a new game from the standard starting position; no prior state is restored. *(Persistence is intentionally out of scope per Clarifications session 2026-04-26.)*

---

### User Story 4 - Watch the AI Play Itself (AI vs AI) (Priority: P3)

The user starts a self-play game where the AI plays both sides at configurable strengths and watches it unfold on the board with optional move-by-move commentary (move played, evaluation, principal variation).

**Why this priority**: Self-play is required by the Constitution ("Engine MUST support self-play AI vs AI"), and it is a strong learning and demonstration tool. It is straightforward once Stories 1–2 exist and can be tested independently.

**Independent Test**: With Story 1 in place, the user selects "AI vs AI", picks two strength levels, and starts the game. The board updates automatically with each AI move until the game ends in a legal terminal state.

**Acceptance Scenarios**:

1. **Given** the user starts an AI-vs-AI game with both sides at maximum strength, **When** the game runs to completion, **Then** every move is legal, the game ends in a valid terminal state (mate/draw), and the entire move history is available for review.
2. **Given** a self-play game is running, **When** the user pauses the game, **Then** the AI stops searching and the board state is preserved; resuming continues from the same position.
3. **Given** a self-play game with mismatched strengths (e.g., maximum vs beginner) over a representative sample, **When** many such games are played, **Then** the stronger side wins the clear majority (verifiable via SC-003).

---

### User Story 5 - Get Engine Hints and Position Analysis (Priority: P3)

During a human-vs-AI game, the user can request a hint (the engine's top suggested move) and see a brief position analysis (numerical evaluation, best line). Analysis runs against the same single engine — no separate analysis engine.

**Why this priority**: Adds clear training value with low incremental cost once the engine is wired up. It deepens user engagement without violating the single-engine principle.

**Independent Test**: With Story 1 in place, the user requests a hint at a random middlegame position and receives a single suggested legal move plus a numeric evaluation in pawn-units (e.g., "+0.45") and a short principal variation, all within the configured analysis time budget.

**Acceptance Scenarios**:

1. **Given** an in-progress game on the user's turn, **When** the user requests a hint, **Then** the engine returns one legal suggested move along with a numeric evaluation and principal variation within the configured analysis time budget.
2. **Given** a position with a forced mate in N, **When** the user requests analysis, **Then** the analysis reports the correct mate distance and a winning line (verifiable on a standard tactical test suite — see SC-002).

---

### Edge Cases

- **No legal moves available**: Position must be classified correctly as checkmate (king in check, no legal move) or stalemate (king not in check, no legal move).
- **Special moves**: Castling rights tracked even when the king/rook moves and returns; en passant only legal on the immediately following move; pawn promotion offers all four piece choices and is mandatory.
- **Repetition and 50-move rule**: Both must be detected on the move that creates the third repetition or the 100th half-move without a pawn move or capture.
- **Engine time exhausted**: If the engine has not produced a move when its time budget elapses, it MUST return the best move found so far. The AI never makes an illegal move and never returns no move.
- **Mid-move shutdown**: If the user closes the app while the AI is thinking, the application shuts down cleanly within **≤ 200 ms** of the close request (no zombie processes, no crashes). The current game is discarded (persistence is out of scope).
- **Promotion at game end**: A promotion that delivers checkmate is correctly recognized and notated.
- **Underpromotion**: Promotion to knight/bishop/rook is supported and notated correctly.
- **Long games**: Games of 300+ plies remain stable in memory and history navigation.
- **High-conflict positions**: Positions with many legal moves and deep tactics still return a move within the time budget; if the time budget is unrealistically small (e.g., 1 ms), the engine still returns a legal move.

## Requirements *(mandatory)*

### Functional Requirements

**Game and Rules**

- **FR-001**: System MUST implement the complete FIDE rules of chess: piece movement, captures, check, checkmate, stalemate, castling (kingside/queenside, with all rights), en passant, pawn promotion (Q/R/B/N), the 50-move rule, threefold repetition, and insufficient-material draw.
- **FR-002**: System MUST validate every user-entered move and reject illegal moves with a clear, human-readable reason (e.g., "would leave king in check", "blocked by own piece").
- **FR-003**: System MUST detect and announce game termination immediately on the move that triggers it: checkmate, stalemate, 50-move rule, threefold repetition, insufficient material, or resignation.
- **FR-004**: System MUST allow either color to be played by the human in Human vs AI mode, and MUST allow setting up the initial position as the standard starting position (initial-position custom setup is out of scope for v1).

**Engine**

- **FR-005**: System MUST contain **exactly one chess engine**, integrated at the source level as a logical module statically linked into the same binary as the UI. There MUST be no fallback engine, no ensemble, no runtime engine selection, no engine subprocess, and no inter-process communication (UCI stdin/stdout bridge or otherwise) between UI and engine.
- **FR-006**: The engine MUST be objectively top-tier — competitive with the strongest publicly available chess engines on commodity hardware (target: at least **3400 Elo** on a modern multi-core CPU at standard time controls — see SC-001/SC-002). The engine MUST use **NNUE-style evaluation** (Efficiently Updatable Neural Network with sparse incremental updates and integer SIMD inference; CPU-only, no GPU required) combined with alpha-beta search, iterative deepening, transposition tables, and multi-threaded SMP.
- **FR-006a**: The NNUE network weights MUST be **embedded statically into the application binary at build time** (e.g., via Rust's `include_bytes!`) — not loaded from disk at runtime, not downloaded, not trained. The chosen network MUST be released under a license compatible with GPL-3.0 (permissive licenses such as MIT, Apache-2.0, BSD, CC0, or GPL-3.0 itself are all acceptable). **Amended 2026-04-26**: the original wording forbade GPL infection; the project now explicitly accepts GPL-3.0 because the selected engine (Carp 3.0.1) is GPL-3.0, and the entire `chess-app` executable is therefore distributed under GPL-3.0. The permissive-only constraint applied only to the NNUE network and is now relaxed accordingly. Stockfish's GPL-2-derived networks remain compatible (GPL-3.0 inbound).
- **FR-007**: The engine MUST support both Human vs AI and AI vs AI play.
- **FR-008**: The engine MUST always return a legal move when asked, even when its time budget expires; it MUST NOT return illegal moves and MUST NOT return no move while a legal move exists.
- **FR-009**: The engine MUST support a user-selectable **Reproducible Mode** in which, given identical inputs (position, fixed depth or fixed node count, configuration, and a recorded seed), the engine produces a bit-for-bit identical move and principal variation across repeated runs on the same machine. Reproducible Mode runs single-threaded with a fixed RNG seed and fixed search-ordering rules. The **default** (non-reproducible) mode runs multi-threaded for maximum strength and is NOT required to produce identical outputs across runs; this is consistent with the Constitution's "Local-First Deterministic Execution" principle, which requires reproducibility *given the same configuration* — Reproducible Mode satisfies that on demand.
- **FR-009a**: System MUST allow the user to switch between Default (multi-threaded, max strength) and Reproducible Mode at any time before a search begins. The current mode MUST be visible in the UI status panel (FR-019) and recorded in the move-list metadata so analysis output is annotated with the mode used.

**Configuration and Strength Control**

- **FR-010**: System MUST allow the user to choose AI strength via at least named presets (beginner, intermediate, advanced, maximum) AND/OR explicit parameters (search depth and/or time-per-move and/or total time per side).
- **FR-011**: System MUST persist user settings (strength, time control, UI preferences) between sessions in a local file under the user profile.
- **FR-012**: System MUST allow the user to switch sides, start a new game, and resign at any point during a game.

**In-Session History (no on-disk persistence)**

- **FR-013**: *(Removed — game persistence to disk is out of scope per Clarifications session 2026-04-26. The application MUST NOT write game files to disk and MUST NOT read game files from disk.)*
- **FR-014**: System MUST support unlimited Undo and Redo **within the current session only**, including across special moves (castling, en passant, promotion). Closing the application discards all undo/redo history.
- **FR-015**: System MUST keep a complete in-memory move history for the current game, displayable in standard algebraic notation. The history is discarded when the application exits.

**UI**

- **FR-016**: System MUST present a graphical chessboard rendered by a native desktop UI (no browser, no embedded web view as the primary surface).
- **FR-017**: System MUST allow moves to be entered by mouse (click-click and drag-and-drop) and SHOULD allow keyboard entry of moves in algebraic notation.
- **FR-018**: System MUST visually indicate: side to move, last move, legal target squares for a selected piece, check, and check/mate at game end.
- **FR-019**: System MUST display a move list, captured-piece tray, and engine status (thinking / idle / time remaining).

**Hints and Analysis**

- **FR-020**: System MUST provide on-demand hint and position analysis using the same single engine, returning at minimum: best move, numeric evaluation, and principal variation.

**Local-Only Operation**

- **FR-021**: The application MUST function fully without any network connection at runtime — no API calls, no telemetry, no remote inference, no auto-update calls. (Build-time/dev-time tooling is exempt.)
- **FR-022**: All assets the engine needs at runtime (neural-network weights if any, opening book if used, endgame tablebases if used) MUST ship with the application — embedded directly into the binary where size permits, or shipped as locally-loaded files alongside the executable. None MAY be fetched at runtime. The engine code itself is compiled into the same binary (no separate engine binary).

**Distribution**

- **FR-023**: The product MUST be distributed and run as a **single statically-linked native Windows x86-64 executable** (`.exe`) — one binary that contains both the UI and the chess engine. Launching the executable MUST start the full application without any external service, daemon, container, background server, child process, or runtime / JVM / .NET / Python / interpreter installation step. v1 targets Windows x86-64 only; other platforms are out of scope.
- **FR-024**: First launch MUST complete within a reasonable time on commodity Windows x86-64 hardware (see SC-005) without requiring administrator privileges, installer-based setup, or installation of system-wide services. The `.exe` MUST be runnable directly (portable mode) — extraction-only or double-click-from-Downloads use is supported.

### Key Entities *(include if feature involves data)*

- **Game**: A single in-memory chess game. Holds the current position, full move history, side to move, castling rights, en passant target, half-move clock, full-move number, and result (if terminated). Discarded on application exit; not persisted.
- **Position**: A complete board state — piece placement on 64 squares, side to move, castling rights, en passant target, half-move clock, full-move number.
- **Move**: A single legal action — origin square, destination square, optional promotion piece, optional flag for castling/en passant. Carries enough information to be applied and reversed.
- **Engine Configuration**: Strength preset, time control (per-move/per-side/fixed-depth), Reproducible-Mode flag, hint/analysis time budget.
- **User Settings**: Persisted user preferences (default engine configuration, board orientation, theme, default thread count). These are the **only** data the application writes to disk; no game data is persisted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 — Strength**: At its maximum configured strength on a modern 8-core CPU at 60 seconds per move, the engine wins or draws at least **95%** of games against a publicly available reference engine rated 2500 Elo on the CCRL Blitz or CCRL 40/15 list, verified via a 100-game match orchestrated by `cutechess-cli`.
- **SC-002 — Tactics**: At default analysis time of 5 seconds per position, the engine solves at least **90%** of positions in a recognized public tactical test suite (≥ 200 positions covering mates in 1–5 and standard tactical motifs), with no illegal-move outputs.
- **SC-003 — Strength differentiation**: In a 100-game self-play match between the "Beginner" and "Maximum" strength presets at the same time control, the "Maximum" preset wins at least **90%** of decisive games.
- **SC-004 — Time accuracy**: At every per-move time-control setting from 100 ms to 60 s, the engine returns a move within ±20% of the configured budget in at least **99%** of moves over a 1000-move sample, and never exceeds the budget by more than 50%.
- **SC-005 — Cold start**: From a double-click on the executable on a typical home computer with no network access, the user reaches a playable board in **under 5 seconds** on first launch and **under 2 seconds** on subsequent launches.
- **SC-006 — Resource footprint**: At idle with one game open, the application uses no more than **500 MB** of RAM and **0%** sustained CPU. While the engine is searching at default strength, it uses no more than **75%** of available CPU cores by default and respects user-configured limits.
- **SC-007 — Offline integrity**: A first-time installation on a machine that has never had network access — running with **default settings (`[diagnostics] debug_logging = false`)** — can complete a full Human-vs-AI game, including hints and AI-vs-AI, with **zero** outbound network attempts and **zero** disk writes outside the user-settings file at `%APPDATA%\chess-ai\settings.toml` (verified by packet capture and filesystem audit). Optional debug-log files written when `debug_logging = true` are an opt-in user setting and are explicitly out of SC-007's scope.
- **SC-008 — Rules correctness**: The application passes **100%** of a comprehensive FIDE rules test suite (legal-move generation, special moves, draw detection, terminal states) — at least 1000 positions covering all rule categories — with zero false positives or false negatives.
- **SC-009 — Stability**: In a continuous 24-hour run consisting of back-to-back AI-vs-AI games at maximum strength, the application completes all games without crash, memory leak (< 10% growth in steady-state working set), or illegal-move output.
- **SC-010 — User task completion**: In an unmoderated usability test with 10 chess-literate participants, **at least 9 of 10** complete the following tasks unaided within 2 minutes each: (a) start a new game and play 5 moves, (b) change AI strength, (c) undo and redo a move, (d) request a hint, (e) start an AI-vs-AI game.

## Assumptions

- **Implementation language is Rust.** The entire application — UI, game logic, and chess engine — is written in Rust and compiled into a single statically-linked native Windows x86-64 executable (`.exe`). No JVM, no Python runtime, no Electron, no embedded web view.
- The "best Chess AI engine that can be used possibly" is realised as **a top-tier Rust NNUE engine with an embedded permissively-licensed pre-trained network**. Suitable starting points include modern clean-room Rust engines such as Velvet, Carp, Black Marlin, or Akimbo (all NNUE-based, 3300–3500+ Elo, permissively licensed). The specific engine codebase, fork, and NNUE network are design decisions deferred to the planning phase. The engine ships fully trained: NNUE weights are baked into the binary at build time and are never updated, downloaded, or modified after shipping. A clean-room reimplementation is permitted but not required — adopting and adapting an existing strong Rust engine is preferred for SC-001 / SC-002 / FR-006 strength targets.
- The application targets **Windows x86-64 only for v1**. A single statically-linked native `.exe` is produced (Rust target `x86_64-pc-windows-msvc` or `x86_64-pc-windows-gnu`). macOS, Linux, and ARM variants are deferred to a possible v2; nothing in v1 should preclude future cross-compilation, but no resources are spent on porting, testing, or signing for those platforms in v1.
- The native UI is a **single-window Windows desktop application** with a chessboard, move list, status panel, and settings dialog. Specific UI framework selection is deferred to planning, constrained by the Constitution to **native Rust GUI toolkits** that produce a single statically-linked Windows binary with no browser rendering layer (e.g., egui/eframe, iced, Slint, fltk-rs). Tauri and any other webview-based approach are explicitly excluded by the Constitution.
- **No GPU is required.** NNUE evaluation is CPU-only (sparse incremental updates + integer SIMD). The CPU thread count is user-configurable; the **default `max_threads = ⌈0.75 × num_cpus⌉`, clamped to a minimum of 1** (e.g., 6 threads on an 8-core machine, 1 thread on a single-core VM). The embedded NNUE network is sized to fit in the single executable and run on CPU within SC-006 (≤ 500 MB RAM, ≤ 75% CPU cores by default).
- **All twelve existing AI engines from the legacy codebase are out of scope** and will not be carried forward. The legacy code (Spring Boot server, browser frontend, MCP server, DB2 migration, AWS/Azure/Helm/Terraform, all training infrastructure) is removed or moved to an archive folder; the new application is a clean rewrite.
- **MCP / external agent integration, web server, multi-user sessions, tournament mode against multiple engines, OpenAI integration, and cloud deployment are explicitly out of scope** (forbidden by the Constitution).
- **Custom training of the bundled engine by end users is out of scope for v1**. The engine ships fully trained.
- **Single-game-at-a-time** in the UI: only one active game is open at a time. No game library, no tabbed multi-game UI, no saved games (game persistence is out of scope per Clarifications session 2026-04-26).
- **Standard starting position only for v1** for new games. Custom position setup (FEN entry, position editor) is out of scope for v1.
- **Disk I/O is limited to the user-settings file only.** No game files, no PGN, no exports, no logs (beyond optional debug logging the user explicitly enables).
- **Time controls in v1**: per-move time, fixed depth, and total time per side. Increment-based clocks (e.g., 5+3) are nice-to-have but not required for the MVP.
- The legacy `docs/` folder is retained for reference only; nothing in it is normative for this rewrite.
