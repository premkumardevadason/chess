# Quickstart: Rust-to-C++26 Migration

**Feature**: `002-rust-cpp26-rewrite` · **Date**: 2026-05-20

Developer entry: **read [hld.md](./hld.md) and [lld.md](./lld.md) first** → toolchain gate → preview build → parity tests → production build (when unblocked).

---

## 0. Migration status

| Phase | Requirement | Current host (2026-05-20) |
|-------|-------------|----------------------------|
| **Gate** | MSVC + Clang official `/std:c++26` | **Blocked** — only `/std:c++latest` on MSVC |
| **Preview** | `/std:c++latest` or `-std=c++2c` | MSVC ready via vcvars; Clang not installed |
| **Build** | CMake + Ninja | **Not on PATH** |

Run readiness check:

```powershell
.\scripts\toolchain-readiness.ps1
.\scripts\smoke-cpp26.ps1   # after scripts exist; writes toolchain-gate-status.json
```

---

## 1. Engine binary assets (I1)

NNUE and movegen tables are **not** under `crates/chess-engine/nets/`. Copy from:

```text
crates/chess-engine-chess/bins/net.bin          → cpp/chess_engine/bins/net.bin
crates/chess-engine-chess/bins/*.bin            → cpp/chess_core/bins/   (movegen tables)
crates/chess-engine-chess/UPSTREAM-LICENSE      → cpp/chess_engine/UPSTREAM-LICENSE
```

If `bins/` is missing locally, build the Rust workspace once (`cargo build -p chess-engine-chess`) or obtain bins from the Carp upstream release per `research.md` R-6.

---

## 2. Prerequisites (target state)

| Tool | Version | Notes |
|------|---------|-------|
| Windows 10/11 x86-64 | — | |
| Visual Studio Build Tools | 2022+ | "Desktop development with C++" |
| LLVM Clang | 18+ (19+ when `/std:c++26` ships) | `clang++` on PATH |
| CMake | 3.28+ | |
| Ninja | 1.11+ | |
| Rust (reference only) | 1.83+ | `cargo test` for parity harness |

---

## 3. Clone and branch

```powershell
git clone <repo-url> chess
cd chess
git checkout 002-rust-cpp26-rewrite
```

---

## 4. Preview build (allowed during gate)

```powershell
# MSVC preview
cmake --preset msvc-preview
cmake --build --preset msvc-preview

# Clang preview (after Clang installed)
cmake --preset clang-preview
cmake --build --preset clang-preview
```

Set `CHESS_PREVIEW_BUILD=ON` (preset default). Binary is **non-shipping**.

---

## 5. Production build (after gate clears)

```powershell
.\scripts\smoke-cpp26.ps1   # must pass both compilers

cmake --preset msvc-release
cmake --build --preset msvc-release

cmake --preset clang-release
cmake --build --preset clang-release
```

Artifacts: `build/msvc-release/chess-ai.exe`, `build/clang-release/chess-ai.exe`.

---

## 6. Test

```powershell
ctest --preset msvc-preview --output-on-failure

# Parity vs Rust reference
cargo test -p chess-core -p chess-engine
ctest -R parity
```

Target: **95%** Rust tests with equivalent C++ pass (SC-011).

---

## 7. Run

```powershell
.\build\msvc-release\chess-ai.exe
.\build\msvc-release\chess-ai.exe --self-test
```

Offline verification: disconnect network, play Human vs AI, confirm no outbound traffic (SC-007).

---

## 8. Rust reference (during migration)

Rust workspace remains at `crates/` until SC-011:

```powershell
cargo build --release
cargo test
```

After archive: `legacy/rust-crates/` (read-only parity oracle).

---

## 9. Release gate (before shipping)

Per `tasks.md` **Release Gate**: complete engine port (T067–T068), SC-002 tactics (T075), SC-001 Elo match (`scripts/run-sc001-elo-match.ps1` / T083), SC-011 parity (T074), dual release CI (T073), plus SC-005/006 benchmarks (T089–T090), SC-007 offline audit (T082/T084), SC-009 stability (`scripts/run-stability-24h.ps1` / T088), and SC-010 changelog (T091) before tagging a release build.

---

## 10. Out of scope

Same as 001: no game save/load, web UI, cloud, MCP, multi-engine, training, non-Windows platforms.
