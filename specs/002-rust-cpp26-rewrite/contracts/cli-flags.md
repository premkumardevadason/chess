# Contract: Command-Line Flags

**Binary**: `chess-ai.exe` · **Status**: v1 (parity with 001)

Parsing: minimal custom parser or **CLI11** (vendored, header-only) — no external install.

## Flags

```
chess-ai.exe [OPTIONS]

OPTIONS:
    --log-debug          Enable file logging (this run only)
    --reset-settings     Backup and recreate settings.toml
    --portable           Use <exe-dir>\settings.toml
    --version            Print version + engine + network hash; exit 0
    --help, -h           Print help; exit 0
    --self-test          Run 30s sanity suite; exit 0/1; no GUI
```

Behavioural rules identical to `specs/001-chess-ai-rewrite/contracts/cli-flags.md`:

- `--version`, `--help`, `--self-test` mutually exclusive; exit without GUI.
- Unknown flags → exit code 2.
- No positional arguments in v1.

## Version string format

```
chess-ai <semver> (engine <engine-version>, network <net-hash>, compiler <MSVC|Clang>)
```

Compiler tag required for dual-release traceability (SC-013).
