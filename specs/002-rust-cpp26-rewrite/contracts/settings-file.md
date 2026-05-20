# Contract: User Settings File

**Format**: TOML · **Location**: `%APPDATA%\chess-ai\settings.toml` · **Status**: v1 (schema identical to 001)

**Implementation**: `chess_app::settings` using **toml++**; path via `SHGetKnownFolderPath(FOLDERID_RoamingAppData)` + `\chess-ai\settings.toml`.

Schema, field reference, TimeControl grammar, atomic write semantics, corruption recovery, and portable mode (`--portable`) are **unchanged** from `specs/001-chess-ai-rewrite/contracts/settings-file.md`.

**C++-specific notes**:

- Unknown keys: reject parse (strict mode).
- Atomic write: write `settings.toml.tmp` in same directory, `std::filesystem::rename` to `settings.toml`.
- Default `max_threads`: `std::clamp(static_cast<int>(std::thread::hardware_concurrency() * 0.75), 1, max_cpus)`.

See 001 contract for full tables and examples.
