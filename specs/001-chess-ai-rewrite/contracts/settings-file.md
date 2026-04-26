# Contract: User Settings File

**Format**: TOML · **Location**: `%APPDATA%\chess-ai\settings.toml` (Windows) · **Status**: v1 schema

The user-settings file is the **only** state the application writes to disk (per clarification Q3 and FR-015a). This contract describes the on-disk schema, atomicity guarantees, and migration policy.

---

## 1. File location

| Aspect | Value |
|---|---|
| Path | `%APPDATA%\chess-ai\settings.toml` |
| Resolution | `directories` crate `ProjectDirs::from("", "", "chess-ai").config_dir().join("settings.toml")` |
| Created on | First app launch if absent |
| Owner | Current user (no admin / elevation required) |

The directory `%APPDATA%\chess-ai\` is created if missing. No other files are written there in v1 (debug logs, when enabled, go to `%LOCALAPPDATA%\chess-ai\logs\`).

---

## 2. Schema (v1)

```toml
schema_version = 1

[engine]
default_strength = "Maximum"
default_time_control = "PerMove:5s"
max_threads = 6
reproducible_mode_default = false
hint_time_ms = 3000

[ui]
board_orientation = "Auto"
theme = "Standard"
last_human_color = "White"
animate_moves = true

[diagnostics]
debug_logging = false
```

### Field reference

| Path | Type | Default | Constraints |
|---|---|---|---|
| `schema_version` | integer | `1` | MUST equal `1` for v1 |
| `engine.default_strength` | string | `"Maximum"` | one of `Beginner`, `Intermediate`, `Advanced`, `Maximum` |
| `engine.default_time_control` | string | `"PerMove:5s"` | parsed by TimeControl grammar (§3) |
| `engine.max_threads` | integer | `min(num_cpus()*0.75, 8)` | `1 ≤ n ≤ num_cpus()` |
| `engine.reproducible_mode_default` | boolean | `false` | — |
| `engine.hint_time_ms` | integer | `3000` | `100 ≤ n ≤ 60000` |
| `ui.board_orientation` | string | `"Auto"` | one of `WhiteAtBottom`, `BlackAtBottom`, `Auto` |
| `ui.theme` | string | `"Standard"` | one of `Standard`, `HighContrast`, `Dark` |
| `ui.last_human_color` | string | `"White"` | `White` or `Black`; updated on each new game |
| `ui.animate_moves` | boolean | `true` | — |
| `diagnostics.debug_logging` | boolean | `false` | — |

Unknown top-level keys cause a parse failure (`#[serde(deny_unknown_fields)]`). Unknown fields inside known sections are also rejected. This prevents silent acceptance of typos and makes future schema migrations explicit.

---

## 3. TimeControl grammar

The `engine.default_time_control` string conforms to one of:

```
PerMove:<duration>            e.g., PerMove:5s, PerMove:500ms, PerMove:1m
Total:<game>:<inc>            e.g., Total:5m:3s        (5+3 blitz)
FixedDepth:<n>                e.g., FixedDepth:10
FixedNodes:<n>                e.g., FixedNodes:1000000
```

`<duration>` parses via `humantime::parse_duration`. `<n>` is a positive `u64`.

Invalid TimeControl → fall back to `PerMove:5s` and rewrite the file with the corrected value.

---

## 4. Atomicity and durability

### 4.1 Write protocol

1. Serialize current `UserSettings` to TOML in memory.
2. Write to `%APPDATA%\chess-ai\settings.toml.tmp`.
3. Call `fs::rename("settings.toml.tmp", "settings.toml")` — atomic on NTFS.
4. The OS guarantees: after the rename returns, either the new file is fully on disk or the old file is intact.

### 4.2 Debouncing

UI changes that mutate settings call `settings_dirty = true`. A background task wakes every 250 ms; if dirty, performs the write protocol and clears the flag. This means: rapid changes (e.g., dragging a slider) generate at most ~4 writes per second.

### 4.3 On exit

The exit handler performs an immediate flush bypassing the debounce timer, ensuring any pending change reaches disk.

---

## 5. Read protocol

On startup, the application:

1. Resolves the path; creates parent directory if missing.
2. If file is missing → instantiate `UserSettings::default()`, write it, continue.
3. Else read file contents. If read fails (permissions, I/O error) → log warning, use defaults in-memory, do not block startup.
4. Parse TOML. If parse fails → log warning, rename `settings.toml` to `settings.toml.bad-<timestamp>`, write fresh defaults, continue.
5. Validate against schema rules. Out-of-range values are clamped to valid ranges and the file is rewritten.

The application MUST start successfully even with a corrupted, unreadable, or unwritable settings file (degraded to in-memory defaults; the user sees a non-modal warning toast).

---

## 6. Migration policy

For v1, only `schema_version = 1` is recognized. Any other value triggers the same "rename + write defaults" flow as parse failure (with a clear warning so users on a v2 build downgrading to v1 understand their settings were reset).

For v2+, a migration table will be implemented; the first action on any read is to dispatch on `schema_version` to the appropriate migration chain.

---

## 7. Privacy

The settings file contains only user preferences — no usernames, no game history, no telemetry, no machine identifiers. It is safe to share for support purposes.

---

## 8. Test contract

Compliance is verified by `tests/settings_file.rs`:

1. **Round-trip**: For every default `UserSettings` and a hand-crafted populated example, `parse(serialize(s)) == s`.
2. **Atomicity**: A power-loss simulation (kill the writer between step 2 and step 3 of §4.1) leaves the previous valid file intact.
3. **Corrupted file recovery**: Hand-craft a corrupted TOML; assert that startup produces defaults, the bad file is preserved with `.bad-` suffix, and a fresh valid file is written.
4. **Unknown field rejection**: Add an unknown field; assert parse fails with a clear error.
5. **Out-of-range clamping**: Set `max_threads = 999`; assert it is clamped to `num_cpus()` and the file is rewritten.
