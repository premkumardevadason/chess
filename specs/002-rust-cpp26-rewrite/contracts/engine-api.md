# Contract: Engine API (C++)

**Boundary**: `chess_app` ↔ `chess_engine` · **Status**: v1 (parity with 001 Rust contract)

In-process C++ API only. No UCI, sockets, or subprocess.

---

## 1. Engine lifecycle

```cpp
namespace chess_engine {

class Engine {
 public:
  Engine();  // loads embedded NNUE (~30 MB); may take 50–150 ms
  std::unique_ptr<EngineHandle> spawn();
};

}  // namespace chess_engine
```

---

## 2. Command / event channel

```cpp
enum class Command {
  SetPosition,
  StartSearch,
  Stop,
  SetConfig,
  Shutdown,
};

enum class Event {
  SearchProgress,
  SearchComplete,
  SearchAborted,
  Warning,
  Stopped,
};

class EngineHandle {
 public:
  void send(Command const& cmd);           // non-blocking; throws if dead
  std::optional<Event> try_recv();         // non-blocking poll
  Event recv_blocking(std::chrono::milliseconds timeout);
};
```

**Queue**: bounded capacity **8** commands; search thread owns `Engine` state.

### `SetPosition`

```cpp
struct SetPositionPayload {
  chess_core::Position position;
  std::vector<chess_core::Move> history;  // for repetition detection
};
```

### `StartSearch`

```cpp
struct StartSearchPayload {
  EngineConfig config;
};
```

### `SetConfig`

```cpp
struct SetConfigPayload {
  uint8_t max_threads;      // 1 .. hardware_concurrency
  bool debug_logging;
};
```

---

## 3. Progress and result types

```cpp
struct SearchInfo {
  int depth;
  int score_cp;
  std::vector<chess_core::Move> pv;
  uint64_t nodes;
  uint32_t nps;
};

struct SearchResult {
  chess_core::Move best_move;
  int score_cp;
  std::vector<chess_core::Move> pv;
  uint64_t nodes;
};
```

`SearchProgress` events: at most **one per 100 ms** during search (UI throttle).

---

## 4. Modes

| Mode | Threads | Determinism |
|------|---------|-------------|
| `Default` | `max_threads` (Lazy SMP) | Not required across runs |
| `Reproducible` | 1 | Bit-identical PV + move for same inputs + seed |

---

## 5. Error handling

| Condition | Behavior |
|-----------|----------|
| Search time exhausted | Return best move found; never illegal / empty if legal moves exist |
| `Stop` command | Abort within ≤ 50 ms typical; emit `SearchAborted` |
| Engine thread exit | `send()` throws `EngineDead` |

---

## 6. Contract tests (CTest)

| Test | Requirement |
|------|-------------|
| `EngineConstructs` | Init < 500 ms |
| `LegalMoveAlways` | 1000 random positions, time limit 1 ms |
| `ReproducibleTwice` | Same move + PV twice in Reproducible mode |
| `NoRuntimeIO` | Static analysis / link audit: no Winsock, no filesystem in `chess_engine` |

Parity: outputs MUST match Rust `chess-engine` tests in `crates/chess-engine/tests/` within tolerances documented in `tests/parity/README.md`.
