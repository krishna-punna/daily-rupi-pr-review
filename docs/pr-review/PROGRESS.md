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

## Phase 1: App tooling + CI checks
- [ ] `docs/requirements/daily-rupi-requirements.md` (IDs from spec §6.2, checked against code)
- [ ] Consumer contracts `web-frontend.json` (from `api.ts`) and `android-sync.json` + `ConsumerContractTest` (`@Tag("contract")`)
- [ ] JaCoCo XML report on `test`
- [ ] SpotBugs + FindSecBugs (SECURITY only) via `spotbugs:check`; result on `main` reported to the user
- [ ] Surefire excludes `contract` by default; `-Dgroups=contract` runs them
- [ ] Proof on a local scratch branch: `SQL_INJECTION_SPRING_JDBC` detected; renamed `paymentMethodName` fails the contract test
- [ ] `.github/workflows/pr-review.yml` with `backend-tests`, `contract-tests`, `sast` (engine job stubbed)
- [ ] Existing `mvn test` and `ci.yml` unchanged in behaviour
- [ ] Pushed and green on GitHub (after user approval)

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
