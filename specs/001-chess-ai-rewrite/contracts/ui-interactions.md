# Contract: UI Interactions

**Layer**: `chess-app` (egui frontend) · **Status**: v1

This contract specifies the user-facing interactions the GUI MUST support. It is the externally observable behaviour of the desktop application — what the user sees, can click, and can keyboard-drive.

---

## 1. Application windows / screens

The app is a **single-window desktop application** with three logical screens, switched in-place (no native window switching):

| Screen | When entered | Components |
|---|---|---|
| **Game** (default) | App launch | Board (left), move list + clock + buttons (right) |
| **Settings** | User clicks Settings menu / presses `Ctrl+,` | Form for all `UserSettings` fields |
| **About** | User clicks Help → About | Version, network hash, license, links to upstream MIT licenses |

Closing the window terminates the process. There is no system tray, no minimise-to-tray, no auto-launch on startup.

---

## 2. Game-screen interactions

### 2.1 Making a human move

| Method | Action | Effect |
|---|---|---|
| Click-click | Click own piece, then click target square | Move played if legal; piece returns to source if illegal |
| Drag-and-drop | Press on own piece, drag, release on target | Same as above |
| Keyboard SAN entry | Press `Tab`, type `Nf3`, press `Enter` | Move played if it parses to a unique legal move; flash error otherwise |
| Keyboard arrows | Press `Tab`, then arrow keys to navigate, `Space` to select source then target | Move played if legal |

**Rules**:
- The UI MUST highlight all legal target squares of the currently selected piece (FR-018).
- Illegal click attempts MUST be rejected silently (FR-002 — no flash, no error sound by default).
- Promotion: when a pawn reaches the back rank, a 4-button modal appears (Q/R/B/N) and the move is not committed until the user picks one. `Esc` cancels the move.

### 2.2 Engine move trigger

After the human move:

1. UI updates `Game.current` and appends to `Game.history`.
2. UI sends `Command::SetPosition` then `Command::StartSearch` to engine.
3. UI shows "Thinking..." indicator with elapsed time and current depth (from `SearchProgress` events).
4. On `SearchComplete`, UI plays the engine's move with a brief animation (200 ms unless `animate_moves = false`).
5. UI checks for terminal conditions (mate / draw); if found, displays a modal "Game Over: <reason>".

### 2.3 Game controls (right panel)

| Control | Hotkey | Behaviour |
|---|---|---|
| **New Game** | `Ctrl+N` | Confirm modal if game in progress; reset to starting position; clear history; clear redo stack |
| **Undo** | `Ctrl+Z` | Pop last move from history; if engine was thinking, send `Stop` first; takes back the engine's move and the human's move (so it's the human's turn again) |
| **Redo** | `Ctrl+Shift+Z` | Pop from redo stack; replay |
| **Resign** | `R` | Confirm modal; sets `result = Resignation(opponent)` |
| **Hint** | `H` | Send analysis-only search; flash arrow showing engine's recommendation; do not commit |
| **Stop Thinking** | `Esc` | Send `Stop` to engine if mid-search; engine will play its current best move |
| **Toggle Reproducible** | `Ctrl+R` | Toggle `mode = Reproducible { seed }` for next search; status indicator updates |

### 2.4 Move list

- Vertical scrollable list of moves in SAN.
- Each move is clickable to **navigate** (read-only — view past position, do not branch).
- The currently displayed position is highlighted.
- Clicking a past move shows that position in the board view; clicking the latest move returns to live game.
- Branching from a past move (i.e., making a new move from a past position) replaces the redo stack with a new line. (Standard chess-app behaviour.)

### 2.5 Status indicators (always visible on Game screen)

- **Side to move** (highlighted around the appropriate clock or near the player's name)
- **Reproducible Mode**: badge in top-right ("Default" / "Reproducible") — required by FR-009a
- **Engine status**: "Idle" / "Thinking… depth N, eval +0.42"
- **Connection status**: NOT shown (there is no network connection — FR-021)

---

## 3. Settings-screen interactions

A form-style page with sections matching the settings file schema (`engine`, `ui`, `diagnostics`).

| Field | Widget | Live preview |
|---|---|---|
| `default_strength` | Dropdown | Re-applied for the next search |
| `default_time_control` | Dropdown + custom-input row | — |
| `max_threads` | Slider 1..num_cpus | Re-applied for the next search |
| `reproducible_mode_default` | Checkbox | — |
| `hint_time_ms` | Number input (100..60000) | — |
| `board_orientation` | Radio group | Board immediately re-orients |
| `theme` | Dropdown | Theme immediately switches |
| `animate_moves` | Checkbox | Immediate effect on next move |
| `debug_logging` | Checkbox | Immediate effect (next log line goes to file) |

Changes are applied **immediately** to the live UiState. Persistence to disk is debounced at 250 ms.

A "Reset to defaults" button restores all fields to their default values and saves immediately.

---

## 4. About-screen interactions

Read-only. Displays:

- App name and version (e.g., `chess-ai 1.0.0`)
- Engine name and version (e.g., `forked from Carp 3.0.1`)
- NNUE network short hash (e.g., `network: 7a8e91…`)
- Build commit and timestamp
- Link to source repository (clickable; opens default browser)
- Full text of MIT licenses for: Rust toolchain, egui, eframe, the engine fork, the NNUE network

No network calls — all version info is baked at build time via `env!` and `vergen` crates.

---

## 5. Keyboard accessibility (R-16)

All actions in §2.3 MUST be keyboard-accessible. `Tab` cycles focus across: board, move list, hint button, undo button, new-game button, settings link. Focus is visually distinct (high-contrast outline). The board itself is keyboard-navigable: `Tab` enters the board, arrow keys move a focus square, `Space` selects, second `Space` confirms (for two-step move selection).

---

## 6. Performance contract

The UI thread MUST:

- Render at ≥ 30 fps on commodity hardware (SC-007 implication).
- Drag a piece without dropped frames even while the engine is thinking (R-5).
- Respond to keyboard input within 50 ms.

Any operation in the UI thread that takes > 16 ms (one frame at 60 fps) MUST be moved to a background task. Specifically: settings file I/O is debounced and async; all engine computation is on the search thread.

---

## 7. Test contract

UI behaviour is verified by:

1. **`tests/ui_smoke.rs`**: launches the app via `eframe::run_native` with a mock engine and exercises menu, settings, new game, undo via egui's headless test harness.
2. **`tests/board_interaction.rs`**: scripted clicks at known pixel coordinates verify legal move execution and illegal-move rejection.
3. **Manual QA checklist** in `tests/qa-checklist.md` for visual / animation aspects that headless tests can't catch (theme switching, animation smoothness).
