# PR Review PoC — Decisions

Each entry: date, decision, why, alternatives considered.

## 2026-10-15: Separate PoC repository, copied from the app
- **Decision:** the PoC lives in `krishna-punna/daily-rupi-pr-review`, which starts as a full-history copy of `krishna-punna/daily-rupi` at `a1478b2`, with the PoC added on top.
  - Local clone: `D:\NEW_GIT\daily-rupi-pr-review`, with remotes `origin` (PoC repo) and `upstream` (app repo, push URL set to `DISABLED`).
- **Why:**
  - Demo PRs, AI comments, and deliberately vulnerable demo code stay out of the real app repo.
  - The app repo's CI, branches, and open PRs are never affected.
  - The user's primary clone has unrelated uncommitted work and is left untouched.
- **Alternatives:**
  - A PoC inside `daily-rupi` itself.
  - An engine-only repo that reviews `daily-rupi` across repos (more plumbing: tokens, versioning).
- **Consequence:** the app code here is a snapshot. To pick up later app changes, merge `upstream/main` deliberately and regenerate the demo overlays (spec §14).

## 2026-10-15: Requirement wording checked against the code
- **Decision:** `docs/requirements/daily-rupi-requirements.md` keeps the 12 IDs from spec §6.2 and adds an "Enforced in" column. Two wordings are more precise than the spec:
  - REQ-SEC-002 also lists `POST /api/auth/login` (the form-login endpoint) as reachable without logging in, and says the rest of `/api/auth/**` needs any logged-in user, not the USER role.
  - REQ-API-001 also covers `core/auth.service.ts` (`GET /api/auth/me`).
- **Why:** that is what `SecurityConfig` and the frontend actually do. A requirement that disagrees with the code would make the reviewer flag correct code.

## 2026-10-15: Security scan settings
- **Decision:** SpotBugs 4.10.4.1 with FindSecBugs 1.14.0. It reports the SECURITY category only, at threshold Medium, with `effort=Max`. One pattern is excluded: `SPRING_ENDPOINT` (it marks every controller method and is an inventory note, not a defect). It runs on demand with `mvn compile spotbugs:check`, not in `mvn test`.
- **Why:** the scan must catch real injection bugs (proven: `SQL_INJECTION_SPRING_JDBC` is reported at Medium) without burying them in noise.
- **Versions:** the newest releases at least two weeks old on 2026-10-15. spotbugs-maven-plugin 4.10.5.0 was 3 days old, so it was skipped.
- **Existing finding:** `ENTITY_LEAK` in `AdminController.auditLog` (the endpoint returned the `AuditLog` JPA entity).
  - The user chose to fix it in the real `daily-rupi` app rather than add an exclusion here.
  - Fixed in krishna-punna/daily-rupi#11 (returns an `AuditEntry` record with the same six JSON fields).
  - Merged into this repo from `upstream/main` (`eceb894`) on 2026-10-15. No exclusions were needed.

## 2026-10-15: Commit identity
- **Decision:** this folder (and the fix folder used for daily-rupi#11) uses the repo-local Git identity `krishna-punna <64274358+krishna-punna@users.noreply.github.com>`, matching the author of the user's earlier daily-rupi commits. The machine's global Git identity is unchanged.
- **Resolved:** the PoC commits made before this change were re-authored as `krishna-punna` before the first push, so every commit in this repo has that author.

## 2026-10-15: Bringing upstream app changes into the PoC copy
- **Decision:** app changes are made in `krishna-punna/daily-rupi`, then merged into this repo with a normal merge commit (never a rebase of pushed branches).
  - Since Phases 0–1 were merged (2026-10-15), local `main` tracks `origin/main` (the PoC repo), which is ahead of upstream. To bring in app changes:
    1. `git fetch upstream`
    2. `git switch -c sync/upstream-<date> origin/main`
    3. `git merge upstream/main`
    4. Push and merge it as a normal PR, so the PoC checks run on it
    5. Merge `main` into any open `poc/*` branch
  - Before Phases 0–1 were merged, the procedure was `git merge --ff-only upstream/main` on a `main` that tracked upstream.
- **Why:** the PoC must never diverge from the real app by editing app code, so reviews stay about real code. Merging keeps history intact for branches that are already pushed.
- **After each sync:** re-run `mvn test`, `mvn test -Pcontract-tests`, and `mvn compile spotbugs:check`. From Phase 4 on, also check the demo overlays (spec §14).

## 2026-10-15: Contract checks reported by name
- **Decision:** inside the `contract-tests` profile only, Surefire uses the JUnit 5 "phrased" XML reporter, so each dynamic check appears as `web-frontend: Expense list` rather than `[5]`.
- **Why:** the engine (Phase 2) reads these reports to say which consumer and endpoint broke. Plain `mvn test` reports are unchanged.

## 2026-10-15: `pr-review.yml` triggers in Phase 1
- **Decision:** Phase 1 triggers only on `pull_request` (`opened`, `synchronize`, `reopened`, `ready_for_review`, `labeled`) to `main`, for changes under `backend/**`, `docs/requirements/**`, the policy file, or the workflow itself. `pull_request_review` and `workflow_dispatch` arrive with the engine in Phase 5.
- **Why:** before the engine exists, they would only re-run the check jobs. The `labeled` type re-runs the checks on any label; that is acceptable for a PoC.

## 2026-10-15: Phase 2 engine design choices
- **Policy file has a `project` section.** Spec §11.1 shows only the policy keys. The file also says where the reviewed module, source root and contracts live, and how to run each check with Maven, mirroring `pr-review.yml`. Every key is required and unknown keys are rejected, so a typo cannot silently weaken the policy.
- **What becomes a finding:**
  - SAST issues: CONFIRMED when they touch the PR's changed lines (±3), PRE_EXISTING otherwise.
  - Consumer-contract failures: one finding per consumer endpoint, with the lost field as evidence.
  - Coverage: a finding only when a changed file has 3 or more coverable lines and none covered. Partly covered code is left to the W2 diff-coverage warning, to avoid noise.
  - Ordinary unit-test failures: listed in the check's detail (and block through R2), not duplicated as findings.
- **SAST severities:** injection rules are HIGH (CRITICAL at FindSecBugs priority 1). Other rules follow FindSecBugs priority: 1 HIGH, 2 MEDIUM, 3 LOW.
- **No coverage when tests fail.** JaCoCo's report runs after Surefire in the `test` phase, so a failing test run leaves no `jacoco.xml`. CI behaves the same way. The report then says diff coverage is unknown instead of guessing. Such a PR is NOT_READY through R2 anyway.
- **Local check results come from Maven exit codes**, the same signal CI uses. `--reports-dir` instead infers results from the reports alone, and a folder with no readable report is MISSING. GitHub mode (Phase 5) will read the Checks API instead.
- **Merge conflicts (R4) are not checked yet**; the input is "unknown", which never fires R4. Approvals are unknown in local runs, so they count as not given (R7).
- **Console output is plain ASCII**, because Windows consoles often cannot show the Markdown's status symbols. The `.md` file keeps them for GitHub.
- **Test fixtures are real reports, sanitised.** They come from a scratch branch (concatenated `JdbcTemplate` SQL plus a renamed `paymentMethodName`) and from `main`. Machine paths, system properties, logs and JaCoCo session ids were stripped, because the repo is public.
- **Engine CI is its own workflow** (`pr-review-engine.yml`), so engine-only changes don't re-run the app's checks and the engine's tests are not mistaken for an app check.

## 2026-10-15: Java 17 baseline for the engine
- **Decision:** `pr-review/` targets Java 17, the same as `backend/`.
- **Why:** the machine has JDK 17.0.12 only, and CI already uses 17. One JDK is enough.
- **Alternatives:** Java 21 for the engine (would need a second JDK locally and in CI).

## 2026-10-15: New workflow instead of changing `ci.yml`
- **Decision:** the PoC checks (`backend-tests`, `contract-tests`, `sast`) and the engine job live in a new `.github/workflows/pr-review.yml`. `ci.yml` stays unchanged.
- **Why:**
  - It keeps the copied app CI identical to upstream.
  - The engine needs its required checks inside the same workflow so it can wait for them with `needs`.
- **Cost:** on PRs that touch `backend/**`, backend tests run twice (once per workflow). This is acceptable for a PoC.
