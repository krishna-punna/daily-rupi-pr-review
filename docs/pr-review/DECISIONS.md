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
- **Open:** an existing finding on unchanged app code, `ENTITY_LEAK` in `AdminController.auditLog`, awaits the user's decision (see PROGRESS.md).

## 2026-10-15: Contract checks reported by name
- **Decision:** inside the `contract-tests` profile only, Surefire uses the JUnit 5 "phrased" XML reporter, so each dynamic check appears as `web-frontend: Expense list` rather than `[5]`.
- **Why:** the engine (Phase 2) reads these reports to say which consumer and endpoint broke. Plain `mvn test` reports are unchanged.

## 2026-10-15: `pr-review.yml` triggers in Phase 1
- **Decision:** Phase 1 triggers only on `pull_request` (`opened`, `synchronize`, `reopened`, `ready_for_review`, `labeled`) to `main`, for changes under `backend/**`, `docs/requirements/**`, the policy file, or the workflow itself. `pull_request_review` and `workflow_dispatch` arrive with the engine in Phase 5.
- **Why:** before the engine exists, they would only re-run the check jobs. The `labeled` type re-runs the checks on any label; that is acceptable for a PoC.

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
