# Contract: Command-Line Flags

**Binary**: `chess-ai.exe` · **Status**: v1

The application is a desktop GUI app; CLI flags are minimal and intended for support, testing, and reproducibility — not for primary use. Launching with no flags opens the GUI normally.

---

## Flags

```
chess-ai.exe [OPTIONS]

OPTIONS:
    --log-debug
        Enable file logging at WARN+ level to %LOCALAPPDATA%\chess-ai\logs\.
        Equivalent to setting [diagnostics] debug_logging = true in settings.toml
        for this run only (does NOT persist).

    --reset-settings
        Move existing settings.toml to settings.toml.bak-<timestamp> and
        write a fresh defaults file. Then continue startup normally.

    --portable
        Treat the directory containing chess-ai.exe as the data root.
        settings.toml is read from / written to <exe-dir>\settings.toml
        instead of %APPDATA%. Logs go to <exe-dir>\logs\.

    --version
        Print "chess-ai <version> (engine <engine-version>, network <net-hash>)"
        and exit with code 0.

    --help, -h
        Print this help text and exit with code 0.

    --self-test
        Run a built-in 30-second sanity test:
          - Engine initializes
          - 1000-position rules suite passes
          - Reproducible Mode produces identical outputs across two runs
        Print summary to stdout. Exit code 0 on success, 1 on failure.
        Useful for CI and bug-report reproduction. Does not open the GUI.
```

## Behavioural rules

- **Mutually exclusive**: `--version`, `--help`, and `--self-test` each cause the app to exit without opening the GUI. They are mutually exclusive — passing more than one is an error (exit code 2).
- **Forward-compatible**: Unknown flags cause an error (exit code 2) rather than silent ignore. This is enforced by `clap`'s `unknown_args = false` (default).
- **No positional arguments**: v1 has no positional args. Passing one is an error.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Normal exit (GUI closed, `--version`, `--help`, `--self-test` passed) |
| 1 | Self-test failure |
| 2 | CLI parse error / mutually exclusive flags |
| 3 | Engine init failure (NNUE network corrupted, AVX/SSE missing) |
| 4 | Settings I/O failure that prevented startup (extremely rare; settings file system must be unusable) |
| 101 | Rust panic (default) |

## Test contract

Compliance is verified by `tests/cli.rs`:

1. `chess-ai --version` exits 0 with one line of output matching `chess-ai \d+\.\d+\.\d+`.
2. `chess-ai --help` exits 0 with output containing `chess-ai.exe`.
3. `chess-ai --self-test` exits 0 within 60 seconds.
4. `chess-ai --version --help` exits 2.
5. `chess-ai --bogus-flag` exits 2.
6. `chess-ai --reset-settings` (in temp HOME) renames an existing settings file with `.bak-` prefix.
