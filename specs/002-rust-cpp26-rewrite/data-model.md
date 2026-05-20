# Phase 1 Data Model: Rust-to-C++26 Migration

**Feature**: `002-rust-cpp26-rewrite` · **Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md) · **Date**: 2026-05-20

In-memory and on-disk model for the C++26 application. **Parity target**: `specs/001-chess-ai-rewrite/data-model.md` (Rust types). No database, no game persistence.

---

## Entity overview

| Entity | Library | Lifetime | Persisted? |
|---|---|---|---|
| `Position` | `chess_core` | Immutable snapshots + current | No |
| `Move` | `chess_core` | Per move | No |
| `Game` | `chess_core` | Per game | No |
| `MoveRecord` | `chess_core` | Per move in `Game` | No |
| `GameResult` | `chess_core` | Per game | No |
| `EngineConfig` | `chess_engine` | Per search | Subset via `UserSettings` |
| `SearchInfo` | `chess_engine` | Per progress event | No |
| `SearchResult` | `chess_engine` | Per completed search | No |
| `UserSettings` | `chess_app` | Cross-session | **Yes** — TOML |
| `UiState` | `chess_app` | Per session | No |

---

## 1. `Position`

**C++ type**: `chess_core::Position` (move-only, cheap to copy via `Position` holding `shared_ptr` to impl or value-type ≤ 128 bytes).

| Field | C++ type | Notes |
|---|---|---|
| `pieces` | `std::array<Bitboard, 12>` | Per (color × piece-type) |
| `side_to_move` | `Color` | `enum class Color : uint8_t { White, Black }` |
| `castling` | `CastlingRights` | 4-bit bitfield |
| `en_passant` | `std::optional<Square>` | |
| `halfmove_clock` | `uint8_t` | |
| `fullmove_number` | `uint16_t` | |
| `zobrist` | `uint64_t` | Incremental hash |

**API**: `Position make_move(Position const&, Move) -> std::pair<Position, MoveRecord>`; `void unmake_move(Position&, MoveRecord const&)`.

**Validation**: Same rules as 001 data-model §1.

---

## 2. `Move`

**C++ type**: `chess_core::Move` — `uint16_t` bit-packed or `struct Move { Square from, to; Promotion promo; MoveFlags flags; }`.

Encoding matches Rust 16-bit layout for parity harness byte comparison.

---

## 3. `MoveRecord`

**C++ type**: `chess_core::MoveRecord`

| Field | C++ type |
|---|---|
| `move_played` | `Move` |
| `captured_piece` | `std::optional<PieceType>` |
| `prior_castling` | `CastlingRights` |
| `prior_en_passant` | `std::optional<Square>` |
| `prior_halfmove_clock` | `uint8_t` |
| `prior_zobrist` | `uint64_t` |
| `san` | `std::string` (or `std::array<char, 8>` fixed) |

---

## 4. `Game`

**C++ type**: `chess_core::Game`

| Field | C++ type |
|---|---|
| `history` | `std::vector<MoveRecord>` |
| `current` | `Position` |
| `result` | `std::optional<GameResult>` |
| `undo_stack` / `redo_stack` | `std::vector<MoveRecord>` indices or split deques |

Supports unlimited undo/redo (FR-015).

---

## 5. `GameResult`

**C++ type**: `chess_core::GameResult` — `enum class` with variants: Checkmate, Stalemate, FiftyMoveRule, ThreefoldRepetition, InsufficientMaterial, Resignation.

---

## 6. `EngineConfig`

**C++ type**: `chess_engine::EngineConfig`

| Field | C++ type |
|---|---|
| `strength` | `StrengthPreset` enum |
| `time_control` | `TimeControl` variant struct |
| `mode` | `SearchMode` — `Default` or `Reproducible{uint64_t seed}` |
| `max_depth` | `std::optional<int>` |

---

## 7. `SearchInfo` / `SearchResult`

**C++ types**: `chess_engine::SearchInfo`, `chess_engine::SearchResult`

`SearchResult` contains: `Move best_move`, `int score_cp`, `std::vector<Move> pv`, `uint64_t nodes`.

---

## 8. `UserSettings`

**C++ type**: `chess_app::UserSettings` — mirrors [contracts/settings-file.md](./contracts/settings-file.md) schema v1.

Serialized via toml++ to `%APPDATA%\chess-ai\settings.toml`.

---

## 9. `UiState`

**C++ type**: `chess_app::UiState`

| Field | Purpose |
|---|---|
| `screen` | `Game` \| `Settings` \| `About` |
| `selected_square` | `std::optional<Square>` |
| `promotion_pending` | `std::optional<Square>` |
| `engine_handle` | `std::unique_ptr<EngineHandle>` |
| `status_message` | `std::string` + expiry timestamp |

---

## Parity mapping (Rust → C++)

| Rust crate::type | C++ target |
|---|---|
| `chess_core::Position` | `chess_core::Position` |
| `chess_core::Game` | `chess_core::Game` |
| `chess_engine::EngineHandle` | `chess_engine::EngineHandle` |
| `chess_app::UserSettings` | `chess_app::UserSettings` |

Parity tests MUST compare serialized move lists, perft counts, and search outputs field-for-field where applicable (SC-011).
