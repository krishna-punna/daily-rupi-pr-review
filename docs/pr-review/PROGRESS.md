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
- [x] Existing finding resolved upstream. The scan had reported 1 finding on unchanged app code: `ENTITY_LEAK` (Medium) in `AdminController.auditLog`.
  - Fixed in the real app by krishna-punna/daily-rupi#11 (merged 2026-10-15): the endpoint returns an `AuditEntry` record with the same six JSON fields.
  - Brought in by merging `upstream/main` (`eceb894`) into this branch.
  - After the sync: `mvn test` 35/35 pass, contract checks 13/13 pass, `spotbugs:check` 0 findings, `BUILD SUCCESS`.
- [x] Proof on a local scratch branch (deleted afterwards):
  - Concatenated `JdbcTemplate` SQL is reported as `SQL_INJECTION_SPRING_JDBC` (Medium).
  - Renaming `ExpenseResponse.paymentMethodName` fails exactly 3 contract checks: `web-frontend: Expense list`, `web-frontend: Create expense`, `android-sync: Changes and deletions since a time`.
- [x] `.github/workflows/pr-review.yml` with `backend-tests`, `contract-tests`, `sast`, and a placeholder `pr-review` job gated to `demo/*` or the `ai-review` label. YAML parsed locally with SnakeYAML; actions pinned to commit SHAs
- [x] Existing behaviour unchanged:
  - No diff in `backend/src/main`, `frontend/`, `ci.yml`, `README.md`, or `db/`.
  - `mvn test` still runs the same 35 tests, all passing.
- [x] Pushed and green on GitHub. Merged as PR #2 on 2026-10-15.
  - First `PR review` run (head `7b4f76d`): `backend-tests`, `contract-tests`, and `sast` all succeeded and uploaded their reports.
  - `pr-review` was skipped as designed (not a `demo/*` branch, no `ai-review` label).

**Phase 0 merged as PR #1, Phase 1 as PR #2 (2026-10-15).** Before the first push, every PoC commit was re-authored as `krishna-punna` and Phase 1 was rebased onto `main`.

## Phase 2: Engine core, no LLM (branch `poc/phase-2-engine-core`)
- [x] Domain model:
  - fixed taxonomy (`Category`), `Severity`, `FindingStatus`, `EvidenceLevel`
  - `Finding` with builder, and `FindingSource` (only tool, test, contract and coverage sources count as independent evidence)
  - line-independent `Fingerprint`
- [x] `UnifiedDiffParser`, which reads hunks by their line counts, not by line prefixes; `ChangedLines` with ±3 tolerance
- [x] `GitCli` and `LocalGitPrSource`: diff from the merge base; the current head is re-read for R1
- [x] Report parsers for SpotBugs/FindSecBugs XML, Surefire XML and JaCoCo XML. The XML reader never resolves external entities or DTDs
- [x] `DiffCoverage`: covered changed lines ÷ changed lines that hold code
- [x] `ToolFindingNormalizer`:
  - A SAST issue in the changed lines is CONFIRMED; one outside them is PRE_EXISTING.
  - Each broken consumer contract becomes its own finding, naming the field that was lost.
  - A changed file with 3 or more coverable lines and 0 covered becomes a TEST finding.
  - Findings are numbered F-001… with blocking ones first.
- [x] `review-policy.yml` with a `project` section (check commands mirror CI). `PolicyLoader` rejects missing or unknown keys
- [x] `ReadinessEngine`: a pure function implementing R1–R8 and W1–W5. Table-driven tests: 31 rows plus 2 more tests, none of them demo scenarios
- [x] `LocalCheckRunner`: runs the CI checks in a throwaway worktree, and only deletes files inside it. `ReportBasedChecks` infers results from existing reports; a folder with no reports is MISSING
- [x] `ReviewAnalyzer`, `ReviewReport` (JSON), `MarkdownRenderer` (future PR comment; untrusted text escaped), `FileStateStore` (one analysis per head SHA), `AuditLog` (JSONL, no code)
- [x] CLI: `pr-review review local --base --head [--reports-dir] [--check name=STATUS] [--out] [--keep-worktree]`
- [x] Engine CI: `.github/workflows/pr-review-engine.yml` (`engine-tests`)
- [x] Tests: 80 pass (`mvn -f pr-review/pom.xml package`). Parser, analyzer and CLI tests use real backend reports (sanitised) in `src/test/resources/reports/`
- [x] **DoD proof (2026-10-15):** `review local --base main --head scratch/phase-2-defective` on this repository took about 2 minutes. The scratch branch concatenated SQL and renamed `paymentMethodName`. The result was NOT_READY from deterministic evidence alone:
  - R2: all three checks failed.
  - R5: F-001 SQL injection (FindSecBugs, CWE-89) and F-002–F-004 broken consumer contracts, each naming the missing field.
  - R7: human approval needed.
- [x] Contrast run: `review local --base eceb894 --head main` (the merged Phase 0–1 work) gave READY_FOR_HUMAN_APPROVAL. All three checks passed, there were 0 findings, and only R7 fired
- [ ] Pushed and merged (waiting for the user's go-ahead)

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
