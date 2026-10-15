# PR Review PoC — Progress

Phases follow `POC_SPEC.md` §18. A box is ticked only when its Definition of Done ran and passed.

## Phase 0: Bootstrap (branch `poc/phase-0-bootstrap`)
- [x] Spec committed at `docs/pr-review/POC_SPEC.md`
- [x] PoC repo `daily-rupi-pr-review` set up locally as a full-history copy of `daily-rupi` at `a1478b2` (`origin` = PoC repo, `upstream` = app repo with push disabled). The user's primary clone is untouched. Not pushed yet
- [x] Prerequisites checked: git, JDK 17.0.12, Maven 3.10, Node present; `gh` missing; `ANTHROPIC_API_KEY` not set
- [x] Baseline `cd backend && mvn test` passes on `main` (2026-10-15: 35 tests in 6 classes, 0 failures, ~48 s)
- [x] `CLAUDE.md`, `PROGRESS.md`, `DECISIONS.md` drafted
- [x] `pr-review/` skeleton (Spring Boot 3.5.6 + picocli 4.7.7, Java 17) and `.gitignore` entries (`.ai-review/`, `pr-review/target/`)
  - `mvn -f pr-review/pom.xml package`: 2 tests pass
  - `java -jar pr-review/target/pr-review.jar --version` prints `pr-review 0.1.0-SNAPSHOT`
- [x] User approved the Phase 0–1 plan (2026-10-15)

## Phase 1: App tooling + CI checks (branch `poc/phase-1-app-tooling`)
- [x] `docs/requirements/daily-rupi-requirements.md`: 12 IDs from spec §6.2, each with where the code enforces it
- [x] Consumer contracts `web-frontend.json` (10 endpoints, from `api.ts` + `auth.service.ts`) and `android-sync.json` (3 endpoints) + `ConsumerContractTest` (`@Tag("contract")`)
  - `mvn test -Pcontract-tests`: 13 checks pass, each reported by name (e.g. `web-frontend: Expense list`)
- [x] JaCoCo XML report on every `test` run (`backend/target/site/jacoco/jacoco.xml`)
- [x] Surefire excludes `contract` by default; `-Pcontract-tests` runs only them
- [x] SpotBugs 4.10.4.1 + FindSecBugs 1.14.0 (SECURITY category, threshold Medium, `SPRING_ENDPOINT` excluded) via `mvn compile spotbugs:check`
- [ ] **Waiting on the user:** the scan reports 1 existing finding on unchanged app code: `ENTITY_LEAK` (Medium) in `AdminController.auditLog` (`AdminController.java` lines 31–33). Until it is decided, `sast` fails on `main`
- [x] Proof on a local scratch branch (deleted afterwards):
  - Concatenated `JdbcTemplate` SQL is reported as `SQL_INJECTION_SPRING_JDBC` (Medium).
  - Renaming `ExpenseResponse.paymentMethodName` fails exactly 3 contract checks: `web-frontend: Expense list`, `web-frontend: Create expense`, `android-sync: Changes and deletions since a time`.
- [x] `.github/workflows/pr-review.yml` with `backend-tests`, `contract-tests`, `sast`, and a placeholder `pr-review` job gated to `demo/*` or the `ai-review` label. YAML parsed locally with SnakeYAML; actions pinned to commit SHAs
- [x] Existing behaviour unchanged:
  - No diff in `backend/src/main`, `frontend/`, `ci.yml`, `README.md`, or `db/`.
  - `mvn test` still runs the same 35 tests, all passing.
- [ ] Pushed and green on GitHub (waiting for the user's go-ahead; nothing pushed yet)

## Phase 2: Engine core, no LLM
- [ ] Not started

## Phase 3: AI review + validation
- [ ] Not started

## Phase 4: Lifecycle + fix verification
- [ ] Not started

## Phase 5: GitHub integration
- [ ] Not started

## Phase 6: Dashboard + API
- [ ] Not started

## Phase 7: Evaluation + demo pack
- [ ] Not started
