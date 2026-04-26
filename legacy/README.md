# Legacy code (preserved, not built)

This directory contains the **legacy chess application** that was retired by the
`001-chess-ai-rewrite` feature. It is preserved here verbatim for historical
reference, regression comparison, and so that the rewrite can mine specific
algorithms / heuristics from it without disturbing the new Cargo workspace.

> **Nothing in this directory is built by `cargo build` or by the new CI
> workflow.** The new chess-ai application lives in `crates/` at the repo root.

## What lives here

| Path | What it is | Why preserved |
|---|---|---|
| `src/` | Spring Boot 3 + Java 21 application source. Twelve AI engines (AlphaZero, AlphaFold3, A3C, DQN, Genetic, Leela, MCTS, Negamax, Q-Learning, OpenAI, CNN, Monte Carlo), DB2 migration subsystem, MCP server, REST controllers, training services. | Reference for chess-rules edge cases, opening books, training-game corpora. |
| `frontend/` | TypeScript / React frontend (chess board UI, training dashboards). | Reference for UX flows we are intentionally re-creating natively in egui. |
| `infra/` | Terraform, Helm, Kubernetes, GitHub Actions deploy workflows for cloud deployments. | Reference only — the rewrite is local-first single-binary; cloud infra is explicitly out of scope. |
| `target/` | Maven build output (compiled `.class` files, packaged JAR). | Kept for one-off "boot the legacy app to compare behaviour" experiments. Gitignored via `legacy/target/` in root `.gitignore`. |
| `wireshark/` | Captured network traces from the legacy app's MCP / OpenAI / DB2 traffic. | Used to motivate the "no network at all" Constitution principle. |
| `.settings/`, `.project`, `.classpath`, `.factorypath` | Eclipse IDE project metadata. | If you need to open the legacy code in Eclipse or IntelliJ. |
| `.github/workflows/security.yml` | Legacy security-scan CI workflow. | Was scanning Java/Maven; superseded by the Rust workspace's `ci.yml`. |
| `pom.xml`, `spotbugs-include.xml` | Maven build descriptor + SpotBugs config. | If anyone needs to rebuild the legacy app via `mvn package`. |
| `*.bat`, `*.ps1`, `*.sh` | Launch scripts (`start-chess-dual.*`, `run-modes.bat`, `test-mcp.bat`, etc.). | Reference for the legacy app's runtime modes and MCP integration. |
| `README-MCP.md` | Legacy MCP server documentation. | Reference. |

## What is NOT in `legacy/`

Kept at the repo root because it applies to the rewrite as well:

- `docs/` — project-wide documentation (read-only reference per Phase 1 setup).
- `.cursor/`, `.specify/`, `specs/` — tooling and specifications for the rewrite.
- `.github/CODEOWNERS` and the new `.github/workflows/ci.yml` — apply to the new
  workspace.
- `README.md`, `SECURITY.md` — repo-level policy; will be replaced/updated by
  T103 when the rewrite ships.

## Rebuilding the legacy app (if ever needed)

```powershell
cd legacy
mvn -DskipTests package          # produces legacy/target/*.jar
java -jar target/chess-*.jar     # boots the old Spring Boot app
```

This requires Java 21 + Maven 3.9+ on PATH. The new chess-ai workspace does
**not** require either.

## Removal policy

Per the rewrite plan (`specs/001-chess-ai-rewrite/plan.md`), this directory may
be deleted at any point after **v1 is shipped and stable**. Do not delete it
during active development of the rewrite — the rules-engine validation suites
in T030 / T049 may need to cross-check against the legacy app's move-generation
output.
