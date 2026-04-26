# Contract: Engine API

**Crate boundary**: `chess-app` (UI) ↔ `chess-engine` (search/eval) · **Status**: Stable for v1

This contract describes the Rust API surface that the application binary uses to drive the chess engine. It is the **only** API the UI layer is allowed to call into the engine through. There is no UCI, no JSON-RPC, no socket — just a Rust function and channel API inside the same process.

---

## 1. Engine handle

```rust
pub struct Engine {
    /* private */
}

impl Engine {
    /// Construct an engine. Loads the embedded NNUE network into RAM.
    /// Cost: ~50–150 ms (one-time init); allocates ~30 MB for the network.
    pub fn new() -> Self;

    /// Set up a worker thread and return a controller. Engine state is owned
    /// by the worker thread; this handle communicates via channels.
    pub fn spawn(self) -> EngineHandle;
}
```

## 2. Channel-based controller

```rust
pub struct EngineHandle {
    /* private: holds Sender<Command> and Receiver<Event> */
}

pub enum Command {
    /// Replace the engine's notion of the current position.
    SetPosition { position: Position, history: Vec<Move> },

    /// Begin a search with the given config. The engine will emit
    /// SearchProgress events periodically and exactly one SearchComplete
    /// or SearchAborted event at the end.
    StartSearch { config: EngineConfig },

    /// Stop any in-flight search as soon as possible. Search will emit
    /// SearchAborted (or SearchComplete if it had already finished).
    Stop,

    /// Update non-search-time configuration (e.g., max threads).
    /// Takes effect at the next search.
    SetConfig { max_threads: u8, debug_logging: bool },

    /// Shut down the engine thread cleanly.
    Shutdown,
}

pub enum Event {
    /// Periodic in-progress info (depth, eval, PV, nodes, nps).
    /// Emitted at most every ~100 ms during search to avoid UI flooding.
    SearchProgress(SearchInfo),

    /// Final result with the chosen move.
    SearchComplete(SearchResult),

    /// User-initiated abort.
    SearchAborted,

    /// Non-fatal warning, e.g., "AVX2 not detected, using SSE2 fallback".
    Warning(String),

    /// Engine thread is exiting (after Shutdown).
    Stopped,
}

impl EngineHandle {
    /// Send a command. Non-blocking; bounded channel of size 8.
    /// Returns Err if the engine thread has already exited.
    pub fn send(&self, cmd: Command) -> Result<(), EngineDead>;

    /// Try to receive an event without blocking.
    pub fn try_recv(&self) -> Option<Event>;

    /// Drain all pending events. Called by the UI on each frame.
    pub fn drain(&self) -> Vec<Event>;
}
```

## 3. Behavioural contract

### 3.1 Command ordering and idempotency

- The engine processes `Command`s in FIFO order.
- `Stop` is processed **immediately** (within ≤ 50 ms) — the search aborts on the next node.
- `Stop` while idle is a no-op.
- `StartSearch` while a search is already running is **not** valid; the UI MUST send `Stop` first and wait for `SearchAborted` (or `SearchComplete`) before sending a new `StartSearch`. (The engine will assert this in debug builds and ignore the second `StartSearch` in release builds.)
- `SetPosition` while a search is running is **not** valid — same as above.

### 3.2 Search timing guarantees (FR-007 / SC-009)

- For `TimeControl::PerMove(t)`, the engine MUST return `SearchComplete` within `t + 200 ms` (200 ms tolerance for thread sync and final move-ordering).
- For `TimeControl::FixedDepth(d)`, no time guarantee — search runs to completion. Used for testing only.
- For `TimeControl::FixedNodes(n)`, no time guarantee. Used for benchmarking.

### 3.3 Legal-move guarantee (FR-008)

- `SearchResult::BestMove::mv` MUST be a legal move on the position passed to `SetPosition`. This is enforced by the engine's move generator and by an internal assertion before emit.

### 3.4 Reproducibility guarantee (FR-009 / R-7)

- Two calls with `EngineConfig` having `mode = Reproducible { seed }` and the same `(Position, history, time_control)` MUST produce bit-for-bit identical `SearchResult.mv` and `SearchInfo.pv`.

### 3.5 No I/O guarantee (FR-021 / FR-022)

- The engine thread MUST NOT open any file, socket, or system resource at any time during `StartSearch`. (The NNUE network is already in RAM at `Engine::new`.) This is checked by a `cargo test --features no-io-audit` build that wraps `std::fs` and `std::net` calls in panicking shims.

### 3.6 Resource bounds (SC-006)

- Engine memory steady-state ≤ 200 MB (network + transposition table + thread-local stacks).
- Engine CPU at idle: 0% (worker thread is parked on the channel).

### 3.7 Cancellation latency

- After `Stop`, the engine MUST emit `SearchAborted` within 50 ms on commodity hardware. This is verified by `tests/cancel_latency.rs`.

---

## 4. Type re-exports

The `chess-app` crate accesses these types through `chess_engine::*`:

- `Engine`, `EngineHandle`, `Command`, `Event`, `EngineDead`
- `EngineConfig`, `Mode`, `TimeControl`, `StrengthPreset`
- `SearchInfo`, `SearchResult`, `Eval`

`Position`, `Move`, and `MoveRecord` are re-exported from `chess-core` (which `chess-engine` depends on).

---

## 5. Versioning

This is **v1** of the Engine API. Breaking changes in v2 (e.g., async-style interface, multi-position analysis) will require a major version bump and a coordinated change in `chess-app`.

---

## 6. Test contract

Compliance with this contract is verified by the integration test `tests/engine_contract.rs`, which:

1. Spawns an engine, sends `SetPosition` for each of 20 positions including `Kiwipete`, sends `StartSearch` with `FixedDepth(8)`, asserts `SearchComplete` arrives, asserts `mv` is in `legal_moves(p)`.
2. Sends `StartSearch` with `PerMove(2s)`, sleeps 100 ms, sends `Stop`, asserts `SearchAborted` arrives within 50 ms.
3. Sends two `Reproducible { seed: 0xDEAD_BEEF }` searches at the same position with `FixedDepth(10)` and asserts `mv` and `pv` are bit-identical.
4. Spawns 16 engines in parallel (stress test) and asserts no panics, no resource leaks across 100 search/stop cycles each.
