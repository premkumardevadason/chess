# Contract: UI Interactions (C++)

**Layer**: `chess_app` (Dear ImGui + SDL2) · **Status**: v1

Externally observable behaviour matches `specs/001-chess-ai-rewrite/contracts/ui-interactions.md` with these implementation notes:

| Area | C++ implementation |
|------|-------------------|
| Screens | `enum class Screen { Game, Settings, About }` in `UiState` |
| Board render | ImGui draw list + texture atlas `assets/pieces.png` |
| Input | SDL mouse/keyboard → same interaction table as 001 §2 |
| Engine bridge | `engine_link.cpp` polls `EngineHandle::try_recv()` each frame |
| Promotion modal | ImGui modal overlay; Esc cancels |
| Status messages | 3 s transient text in right panel |

All acceptance scenarios in spec User Stories 1–4 MUST pass through this UI contract.

Full interaction tables (move entry, engine trigger, controls, settings, AI-vs-AI, hints) are **unchanged** from 001 — refer to that document for step-by-step behaviour.
