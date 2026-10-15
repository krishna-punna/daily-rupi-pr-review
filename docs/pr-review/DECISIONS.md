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
