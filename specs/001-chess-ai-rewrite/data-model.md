# Phase 1 Data Model: Chess AI Rewrite

**Feature**: `001-chess-ai-rewrite` · **Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md) · **Date**: 2026-04-26

This document specifies the in-memory and on-disk data model for the v1 application. There is **no database**, **no game persistence**, and **no remote storage** (per FR-021 / FR-022 / clarification Q3). All entities are in-memory Rust types except for the user-settings file.

The data model is small by design (Constitution Principle V — Minimalism). Each entity below maps directly to a Rust type in either `chess-core` or `chess-app`.

---

## Entity overview

| Entity | Crate | Lifetime | Persisted? |
|---|---|---|---|
| `Position` | `chess-core` | Per-move (immutable snapshots) + current | No |
| `Move` | `chess-core` | Per-move (immutable) | No |
| `Game` | `chess-core` | Per game (cleared on New Game / Exit) | No |
| `MoveRecord` | `chess-core` | Per move within a `Game` | No |
| `GameResult` | `chess-core` | Per game | No |
| `EngineConfig` | `chess-engine` | Per search invocation | Subset persisted via `UserSettings` |
| `SearchInfo` | `chess-engine` | Per search progress event | No |
| `SearchResult` | `chess-engine` | Per completed search | No |
| `UserSettings` | `chess-app` | Persisted across sessions | **Yes** — TOML |
| `UiState` | `chess-app` | Per session | No |

---

## 1. `Position`

Complete description of a board state. Immutable; mutations are performed by producing a new `Position` via `make_move`.

**Fields**:

| Field | Type | Notes |
|---|---|---|
| `pieces` | `[Bitboard; 12]` | One bitboard per (color × piece-type). Indexed by `Piece::index()`. |
| `side_to_move` | `Color` | `White` \| `Black` |
| `castling` | `CastlingRights` | 4-bit bitfield: WK, WQ, BK, BQ |
| `en_passant` | `Option<Square>` | Set only on the move immediately after a double pawn push |
| `halfmove_clock` | `u8` | Half-moves since last pawn move or capture (50-move rule) |
| `fullmove_number` | `u16` | Increments after Black moves; starts at 1 |
| `zobrist` | `u64` | Incrementally maintained for transposition table & repetition detection |

**Validation rules**:
- Exactly one White king and one Black king MUST be present (`pieces[WK].count_ones() == 1`).
- `en_passant`, when `Some`, MUST point to a square on rank 3 (Black just moved) or rank 6 (White just moved).
- `castling` rights MUST be consistent with rook/king positions (king on `e1`/`e8`; rook on `a1`/`h1`/`a8`/`h8`).
- `halfmove_clock <= 100` (otherwise `GameResult::Draw(FiftyMoveRule)` is forced).
- `zobrist` MUST equal `compute_zobrist_from_scratch(self)` after every mutation (debug-build invariant assertion).

**State transitions**: A `Position` itself is immutable. The transition is `(Position, Move) -> Position` via `make_move`, which returns a new `Position` plus a `MoveRecord` storing enough state to undo (FR-014).

**Maps to**: spec entity *Position*.

---

## 2. `Move`

A single chess move. Compact, hashable, copyable. 16 bits is enough.

**Fields** (encoded as `u16`):

| Bits | Field | Range / values |
|---|---|---|
| 0–5 | `from` | 0–63 (square index) |
| 6–11 | `to` | 0–63 |
| 12–13 | `promotion` | `0=None`, `1=Knight`, `2=Bishop`, `3=Rook`, `4=Queen` (4 fits in 3 bits, but unified as 4-bit field) |
| 14–15 | `flags` | `00=Normal`, `01=Capture`, `10=Castle`, `11=EnPassant` |

Plus a derived `is_check_after`/`is_mate_after` flag computed lazily for SAN rendering.

**Validation rules**:
- `from != to`.
- If `flags == Castle`, `from` MUST be the king's home square and `to` MUST be `g1`, `c1`, `g8`, or `c8`.
- If `promotion != None`, the moved piece MUST be a pawn and `to` MUST be on rank 1 or rank 8.
- Move legality (does it leave own king in check, is the path clear, etc.) is **not** validated by `Move` itself — that is the move generator's responsibility. `Move` is purely structural.

**State transitions**: None. Immutable.

**Maps to**: spec entity *Move*.

---

## 3. `MoveRecord`

A `Move` paired with the state needed to undo it (for FR-014: unlimited Undo/Redo).

**Fields**:

| Field | Type | Notes |
|---|---|---|
| `move_played` | `Move` | The move itself |
| `captured_piece` | `Option<PieceType>` | What was captured (incl. en-passant) |
| `prior_castling` | `CastlingRights` | To restore on undo |
| `prior_en_passant` | `Option<Square>` | To restore on undo |
| `prior_halfmove_clock` | `u8` | To restore on undo |
| `prior_zobrist` | `u64` | To restore on undo (cheaper than recomputing) |
| `san` | `SmallString<8>` | Standard algebraic notation, computed at make-time |

**Validation rules**: `san` MUST match the move under FIDE SAN rules (e.g., `O-O`, `Nbd2`, `e8=Q+`, `bxa6 e.p.`).

**State transitions**: None — immutable once recorded.

**Maps to**: derived from spec entity *Move* (extended with undo data).

---

## 4. `Game`

A complete in-memory game.

**Fields**:

| Field | Type | Notes |
|---|---|---|
| `start_position` | `Position` | Always the standard starting position in v1 (FR-004) |
| `current` | `Position` | Updated as moves are played |
| `history` | `Vec<MoveRecord>` | All moves played from `start_position` |
| `redo_stack` | `Vec<MoveRecord>` | Cleared whenever a new move is played; populated by Undo |
| `repetition_table` | `HashMap<u64, u8>` | Zobrist hash → count, for threefold detection |
| `result` | `Option<GameResult>` | `None` while in progress |
| `mode` | `GameMode` | `HumanVsAi(Color)` \| `AiVsAi` |

**Validation rules**:
- `current` MUST equal `apply_all(&start_position, &history)` (debug invariant).
- `result` is `Some` iff a terminal condition is satisfied (FR-003).
- Adding a move clears `redo_stack` (standard chess-app behaviour).
- `repetition_table[current.zobrist] >= 3` → `result = Some(GameResult::Draw(ThreefoldRepetition))`.

**State transitions**:
```
              new_game()
                  │
                  ▼
             InProgress ──── make_move ────▶ InProgress
                  │             │
                  │             └─ undo / redo ──▶ InProgress
                  │
                  ▼
              Terminated ◀── result detected (mate / draw / resign)
```

Once `Terminated`, only Undo/Redo and "New Game" actions are permitted (no further `make_move`).

**Maps to**: spec entity *Game* (revised under Q3 — in-memory only, no persistence).

---

## 5. `GameResult`

Enum describing how a game ended.

```rust
pub enum GameResult {
    Checkmate(Color),                 // Color = winner
    Resignation(Color),               // Color = winner; loser resigned
    Draw(DrawReason),
}

pub enum DrawReason {
    Stalemate,
    FiftyMoveRule,
    ThreefoldRepetition,
    InsufficientMaterial,
    AgreedDraw,                       // not exposed in v1 UI
}
```

**Validation rules**:
- `Checkmate(c)` MUST be supported by `current.side_to_move != c && is_in_check(current) && legal_moves(current).is_empty()`.
- `Stalemate` MUST be supported by `!is_in_check(current) && legal_moves(current).is_empty()`.
- `FiftyMoveRule` MUST be supported by `current.halfmove_clock >= 100`.
- `ThreefoldRepetition` MUST be supported by `repetition_table[current.zobrist] >= 3`.
- `InsufficientMaterial` MUST satisfy at least one of: K-vs-K, K+B-vs-K, K+N-vs-K, K+B-vs-K+B (same-coloured bishops).

**Maps to**: spec entities *Game.result* + Edge Cases section.

---

## 6. `EngineConfig`

Per-search configuration handed to the engine on each `Search` command.

**Fields**:

| Field | Type | Notes |
|---|---|---|
| `mode` | `Mode` | `Default { threads: u8 }` \| `Reproducible { seed: u64 }` (R-7) |
| `time_control` | `TimeControl` | `PerMove(Duration)` \| `Total(Duration, Duration)` \| `FixedDepth(u8)` \| `FixedNodes(u64)` |
| `strength` | `StrengthPreset` | `Beginner` \| `Intermediate` \| `Advanced` \| `Maximum` |
| `analysis_only` | `bool` | True for hint/analysis (FR-020) — engine returns top move + eval but does not advance the game |
| `max_threads` | `u8` | User-configurable cap; default = `min(num_cpus * 0.75, 8)` |

**Validation rules**:
- `mode == Reproducible` → engine internally forces `max_threads = 1` and ignores time-based aborts.
- `time_control == FixedDepth(d)` → `1 <= d <= 64`.
- `time_control == PerMove(t)` → `t >= 1ms`.
- `strength != Maximum` introduces a stochastic-blunder mechanism (skill level → reduces effective search depth). Carp's existing `--Skill Level` mapping is reused.

**Maps to**: spec entity *Engine Configuration*.

---

## 7. `SearchInfo` and `SearchResult`

Engine → UI events.

```rust
pub struct SearchInfo {
    pub depth: u8,
    pub seldepth: u8,
    pub nodes: u64,
    pub nps: u64,
    pub time_ms: u64,
    pub eval: Eval,                   // centipawns or Mate(N)
    pub pv: SmallVec<[Move; 16]>,
}

pub enum SearchResult {
    BestMove { mv: Move, info: SearchInfo },
    Aborted,                          // user pressed Stop
    NoLegalMove,                      // should be unreachable; assert!() in debug
}

pub enum Eval {
    Cp(i32),                          // centipawns from side-to-move's POV
    Mate(i32),                        // positive = mating in N; negative = being mated in N
}
```

**Validation rules**:
- `BestMove.mv` MUST be a member of the legal-move set of the position the search was started on (engine MUST NOT return illegal moves; FR-008).
- `info.depth >= 1`.
- `info.pv[0] == mv`.

---

## 8. `UserSettings`

The single piece of persisted state. Stored at `%APPDATA%\chess-ai\settings.toml` (R-8).

**Schema (TOML representation)**:

```toml
schema_version = 1

[engine]
default_strength = "Maximum"        # "Beginner" | "Intermediate" | "Advanced" | "Maximum"
default_time_control = "PerMove:5s" # "PerMove:<duration>" | "Total:<game>:<inc>" | "FixedDepth:<n>" | "FixedNodes:<n>"
max_threads = 6                     # 1..=num_cpus
reproducible_mode_default = false
hint_time_ms = 3000

[ui]
board_orientation = "Auto"          # "WhiteAtBottom" | "BlackAtBottom" | "Auto" (auto = side the human plays)
theme = "Standard"                  # "Standard" | "HighContrast" | "Dark"
last_human_color = "White"          # used when Auto orientation
animate_moves = true

[diagnostics]
debug_logging = false
```

**Rust representation** (in `chess-app`):

```rust
#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(deny_unknown_fields)]
pub struct UserSettings {
    pub schema_version: u32,
    pub engine: EngineSettings,
    pub ui: UiSettings,
    pub diagnostics: DiagnosticsSettings,
}
```

**Validation rules**:
- `schema_version == 1` for v1. Future versions implement explicit migration.
- `engine.max_threads >= 1` and `<= num_cpus()`.
- `engine.hint_time_ms >= 100` and `<= 60_000`.
- `engine.default_time_control` MUST parse via the TimeControl parser; on parse failure, fall back to `PerMove:5s` and re-write the file.
- Unknown top-level keys cause hard parse failure (`#[serde(deny_unknown_fields)]`) — prevents silent acceptance of typos.

**Persistence semantics**:
- **Read**: on app startup, exactly once. If the file is missing → use built-in defaults and write a fresh file. If parse fails → log a warning, back the bad file up to `settings.toml.bad-<timestamp>`, write defaults.
- **Write**: debounced 250 ms after any settings change. Write-to-tmp + atomic rename. No partial writes.
- **Migration**: future versions must read `schema_version` first, dispatch to the matching parser, and rewrite at the new version. v1 only handles v1.

**Maps to**: spec entity *User Settings* (revised under Q3 — only data the app writes to disk).

---

## 9. `UiState`

Per-session UI state. Not persisted (a new session always starts fresh).

**Fields**:

| Field | Type | Notes |
|---|---|---|
| `current_screen` | `Screen` | `Game` \| `Settings` \| `About` |
| `selected_square` | `Option<Square>` | Set on click; cleared on move |
| `legal_targets` | `BitBoard` | Highlighted squares for `selected_square` |
| `last_move` | `Option<Move>` | Highlighted on the board |
| `dragging` | `Option<DragState>` | Drag-and-drop in flight |
| `pending_promotion` | `Option<(Square, Square)>` | Awaiting Q/R/B/N choice from modal |
| `engine_status` | `EngineStatus` | `Idle` \| `Thinking { since: Instant, info: SearchInfo }` |
| `move_list_scroll` | `f32` | Move-list scroll position |

**Validation rules**: only one of `selected_square` and `dragging` MUST be `Some` at a time.

**Maps to**: derived; not in spec entity list (UI-only state).

---

## Cross-cutting invariants

These invariants are asserted in debug builds and are part of the test suite:

1. **Move legality round-trip**: For every position in the perft suite, every move returned by `legal_moves(p)` is accepted by `make_move(p, m)`, and `unmake_move(make_move(p, m), record) == p` (bit-for-bit including Zobrist).
2. **Repetition consistency**: After every `make_move`, `repetition_table[current.zobrist]` matches a fresh count derived from replaying `history`.
3. **Engine never returns illegal moves** (FR-008): Property test runs random positions through the engine and asserts the returned move is a member of `legal_moves(p)`. This is exercised by `tests/engine_legal.rs`.
4. **Reproducible Mode determinism**: Running the same `(Position, Mode::Reproducible { seed }, FixedDepth(d))` twice MUST produce bit-for-bit identical `SearchResult.mv` and `SearchResult.info.pv`. Tested by `tests/reproducible.rs` over 50 positions.
5. **Settings round-trip**: For any valid `UserSettings`, `parse(serialize(s)) == s`. Tested by quickcheck-style test in `tests/settings_roundtrip.rs`.
