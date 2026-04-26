# Single-Executable AI Chess Constitution

<!-- Example: Spec Constitution, TaskFlow Constitution, etc. -->

## Core Principles

### I. Single-Artifact Integrity (NON-NEGOTIABLE)

The system MUST compile and run as a **single executable application**.

* No distributed components
* No services, containers, or daemons
* No runtime network dependency

All functionality (engine, UI, logic) MUST reside within one process. Any violation requires redesign.

---

### II. Monolithic Architecture (No Frontend/Backend Split)

The system MUST be a **modular monolith**, NOT a distributed architecture.

Explicitly forbidden:

* Frontend/backend separation
* REST/GraphQL APIs
* Web servers
* Browser-based UI

Allowed:

* Logical modularization (engine, UI, state, etc.) within the same binary

---

### III. Single Optimal Engine (NON-NEGOTIABLE)

The system MUST contain **exactly one chess engine**.

* No multiple engines
* No fallback engines
* No ensemble approaches

The engine MUST be:

* Strong (e.g., comparable to Stockfish class engines or justified alternative)
* Efficient for local execution
* Fully integrated (not loosely orchestrated unless justified)

---

### IV. Local-First Deterministic Execution

All computation MUST occur locally and be reproducible.

* No cloud APIs or external inference
* No hidden dependencies
* Deterministic behavior given same inputs/config

From the specification alone, a competent engineer MUST be able to:

* Rebuild the system
* Reproduce outcomes
* Validate correctness

---

### V. Minimalism & Anti-Bloat Doctrine

The system MUST remain intentionally minimal.

* Every component must justify its existence
* Remove anything not essential to:

  * Gameplay
  * Engine strength
  * UI usability

Explicitly reject:

* Plugin architectures
* Over-generalized abstractions
* Premature extensibility

> Simplicity is enforced, not preferred.

---

## Architectural Constraints

* The application MUST be a **thick client UI** (e.g., desktop application)
* UI MUST be directly coupled to engine within same process
* Allowed UI frameworks: native or local (e.g., JavaFX, Swing, Qt, etc.)
* No browser rendering layer (including Electron-style unless strictly local and justified)

### Engine Integration Constraints

* Prefer **source-level integration** over black-box execution
* If external binary (e.g., Stockfish) is used:

  * Justification REQUIRED
  * Communication MUST remain local (e.g., stdin/stdout, no network)
* Engine MUST support:

  * Self-play (AI vs AI)
  * Human vs AI

### Functional Requirements

* Legal move validation
* Game state management
* Configurable difficulty (depth/time)
* Board visualization (native UI)

### Optional (Controlled)

* Opening books (local files only)
* Endgame tablebases (local, optional)

---

## Specification & Development Requirements

The SPECIFY agent MUST produce a **complete reconstructable specification** including:

### System Blueprint

* Single-process architecture diagram
* Logical modules (non-deployable)

### Engine Design / Integration

* Engine selection justification
* Move generation pipeline
* Search strategy (alpha-beta, MCTS, etc.)

### Core Data Structures

* Board representation (e.g., bitboards)
* Move encoding
* Game history model

### Execution Flow

* Game loop
* Turn orchestration
* UI ↔ engine interaction

### Build & Runtime

* Compilation steps
* Runtime requirements
* Zero external service dependencies

---

## Governance

This constitution supersedes all architectural and implementation decisions.

All outputs from the SPECIFY agent MUST pass the following compliance checks:

1. Does the system run as a single executable?
2. Does it function fully offline?
3. Is there exactly one engine?
4. Is there any hidden frontend/backend split?
5. Can the system be rebuilt from the specification alone?

If ANY answer is “No”:
→ The design is INVALID and MUST be reworked.

### Amendment Rules

* Any change MUST preserve:

  * Single-process execution
  * Single-engine constraint
  * Local-first operation
* Any relaxation requires:

  * Explicit justification
  * Impact analysis
  * Migration plan

### Enforcement Principle

> “Strength through simplicity, not through scale.”

The system MUST evolve as a **refined tool**, not an expanding ecosystem.

---

**Version**: 1.0.0 | **Ratified**: 2026-04-26 | **Last Amended**: 2026-04-26
