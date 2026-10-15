# Daily Rupi

Personal daily expenditure tracker: Spring Boot backend (`backend/`), Angular frontend (`frontend/`), MySQL in production.
**This repository (`daily-rupi-pr-review`) is a copy of the Daily Rupi app (upstream `krishna-punna/daily-rupi` at `a1478b2`) that hosts a proof of concept:** an AI pull-request review engine (`pr-review/`) that reviews PRs against the backend.
The PoC's full contract is `docs/pr-review/POC_SPEC.md`. Read the relevant sections before working on the PoC.
Remotes: `origin` is this PoC repo. `upstream` is the real app repo, read-only: its push URL is disabled, so never push to it, open PRs on it, or change its settings.

## Module map

| Path | What it is |
| --- | --- |
| `backend/` | The app, and the code the PoC reviews. Spring Boot 3.5.6, Java 17, JPA + Flyway, Spring Security. Packages: `expense`, `budget`, `dashboard`, `masterdata`, `auth`, `security`, `user`, `audit`, `admin`, `common`, `config` |
| `backend/src/main/resources/db/migration/` | Flyway migrations `V1`–`V5` (Flyway owns the schema; `ddl-auto: none`) |
| `frontend/` | Angular app. API types in `src/app/core/api.ts` (source for the web consumer contract) |
| `db/setup.sql` | One-time MySQL schema and least-privilege user |
| `docs/pr-review/` | PoC spec, `PROGRESS.md`, `DECISIONS.md`, later `DEMO.md`, `architecture.md`, `eval/` |
| `docs/requirements/` | (PoC, Phase 1) requirement IDs the reviewer checks against |
| `pr-review/` | (PoC, Phase 2+) review engine, a separate Maven project |
| `.github/workflows/ci.yml` | Existing CI: `Backend tests`, `Frontend build`. Do not change it |
| `.github/workflows/pr-review.yml` | (PoC, Phase 1+) review checks and engine job |

## Commands

- Backend tests (in-memory H2, no MySQL needed): `cd backend && mvn test`
- Run the backend: needs MySQL 8 and `DB_USERNAME` / `DB_PASSWORD` as environment variables (never stored in the repo).
  PowerShell: `$env:DB_USERNAME="dailyrupi_app"; $env:DB_PASSWORD="..."; mvn spring-boot:run` from `backend/`. It listens on `127.0.0.1:8080`.
- Frontend: `cd frontend && npm install && npm start`, then open http://localhost:4200 (it proxies `/api` to the backend).
- Consumer-contract tests only (web frontend, Android sync): `cd backend && mvn test -Pcontract-tests`. Plain `mvn test` skips them.
- Coverage: every backend test run writes `backend/target/site/jacoco/jacoco.xml`.
- Security scan: `cd backend && mvn compile spotbugs:check` (FindSecBugs, security only; filters in `backend/config/spotbugs/`). Reports go to `target/spotbugsXml.xml` and `target/spotbugsSarif.json`.
- Build and test the review engine: `mvn -f pr-review/pom.xml package` (about 80 tests; CI runs them in `pr-review-engine.yml`).
- Review a local branch as a PR (about 2 minutes, runs the three checks in a throwaway worktree):
  `java -jar pr-review/target/pr-review.jar review local --base main --head <branch>`. Output goes to `.ai-review/` (gitignored).
  `--reports-dir <dir>` skips Maven and reads existing reports instead.
- Engine test fixtures are real backend reports in `pr-review/src/test/resources/reports/`. They are sanitised: machine paths, system properties, logs and hostnames are stripped. Keep it that way when regenerating them, because this repo is public.
- More PoC commands are added here as each phase lands (see `docs/pr-review/PROGRESS.md`).

## App conventions

- DTOs are records grouped in `*Dtos.java`, validated with Bean Validation. Services throw `ApiException(status, code, message)`, and `GlobalExceptionHandler` turns it into `ApiError`. Responses never include stack traces.
- Integration tests use `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(roles = "USER")`, and writes use `.with(csrf())`. Each test uses its own data and months, because tests share one in-memory database.
- Security: deny by default. `/api/admin/**` needs ADMIN, the rest of `/api/**` needs USER, and only `GET /api/auth/csrf` is public. Every write needs CSRF.
- Business rules: README "Rules worth knowing" (amount > 0 with at most 2 decimals, no future dates, inactive entries refused for new expenses, delete rules).
- Comments explain *why*, in plain English. Match the surrounding style.

## PoC rules (non-negotiable)

**Protect the app**
- Changes to `backend/` are additive only: test tooling, contract tests, build plugins. No behaviour changes or refactors of app code.
- `ci.yml`, the README run instructions, and `cd backend && mvn test` keep working exactly as before.
- If a new check fails on existing code (for example, FindSecBugs on `main`), stop and ask the user. Do not change app code to silence it.
- Never touch the upstream repo. In this repo, demo tooling only creates and deletes `demo/*` branches; `main` and `poc/*` change only through the normal phase workflow.
- Demo branches contain deliberately vulnerable code and are never merged.

**Integrity**
- No scenario-specific logic in the engine. The engine never reads `pr-review/demo/scenarios` during a review.
- The LLM never decides merge readiness and never confirms its own finding. `CONFIRMED` needs independent evidence (spec §10).
- Replay fixtures are recorded from real runs. Hand-written ones are named `*.synthetic.json` and labelled SYNTHETIC.
- Never report a metric that was not computed from an actual run.

**Safety**
- Ask the user before: any `git push`, creating or closing PRs, posting PR comments, deleting branches, or changing GitHub settings, labels, branch protection, or secrets.
- Never print, log, or commit secrets (`ANTHROPIC_API_KEY`, `DB_USERNAME`, `DB_PASSWORD`). Report whether a key is set as yes/no only.
- Show the user the prompts and a cost estimate before the first live Claude API call.

## PoC workflow

- Work through the phases in spec §18 one at a time. After each phase, report and wait for the user to say "continue".
- Use branch `poc/phase-<N>-<slug>` with small conventional commits. Each phase reaches `main` through a PR the user approves.
- A phase is done only when its DoD commands have run and passed in the session. Report: Built / How to run / Verified / Not verified / Risks.
- Keep `docs/pr-review/PROGRESS.md` and this file current. Record every deviation from the spec in `docs/pr-review/DECISIONS.md`.

## Environment

- Windows 11, JDK 17.0.12, Maven 3.10 (`mvn`), Git, Node. No Docker, no Python. Target Java 17 everywhere.
- GitHub CLI (`gh`) is needed from Phase 5 for the demo commands.
- The PoC dashboard (`serve`) uses port 8090, because the app uses 8080.
- The user's own clone of the real app (a separate folder) may hold unrelated uncommitted work, so never touch it from here.
