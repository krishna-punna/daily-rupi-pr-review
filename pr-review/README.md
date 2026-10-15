# PR review engine (proof of concept)

An AI pull-request review and merge-readiness engine. It reviews pull requests against the Daily Rupi backend (`../backend`).

The AI's findings are only advice. Deterministic checks (tests, contract tests, SAST, coverage) and human approval decide whether a PR is ready to merge.

**Status:** Phase 2, the deterministic core. No AI review yet; that arrives in Phase 3. See `../docs/pr-review/PROGRESS.md` and the full specification in `../docs/pr-review/POC_SPEC.md`.

## Build

```
mvn -f pr-review/pom.xml package
```

Needs Java 17 or newer. No database, Docker or API key is needed.

## Review a branch locally

Run from the repository root:

```
java -jar pr-review/target/pr-review.jar review local --base main --head <branch>
```

What it does:

1. **Diff.** It treats `<branch>` as a PR into `main` and diffs from their merge base, as GitHub's "Files changed" tab does.
2. **Checks.** It checks the head commit out into a throwaway git worktree under `.ai-review/work/`, so your own checkout is never touched. There it runs the three required checks exactly as CI does: `backend-tests`, `contract-tests` and `sast`.
3. **Findings.** It turns the reports into findings:
   - a security issue in the changed lines (FindSecBugs)
   - a broken consumer contract (it names the field the consumer lost)
   - changed code that no test runs at all (JaCoCo)

   A tool finding outside the changed lines is reported as pre-existing and never blocks.
4. **Readiness.** It applies the readiness rules in `review-policy.yml` (R1–R8, W1–W5) and decides: NOT_READY, NEEDS_INVESTIGATION, READY_FOR_HUMAN_APPROVAL or ELIGIBLE_FOR_MERGE.

It writes these files under `.ai-review/` (gitignored):

| File | What |
|---|---|
| `reports/<pr-id>/<sha>/review-report.md` | Readable summary (later the PR comment) |
| `reports/<pr-id>/<sha>/review-report.json` | Full analysis |
| `reports/<pr-id>/<sha>/checks/`, `logs/` | Raw check reports and Maven logs |
| `state/pr-<pr-id>.json` | Every analysis of that PR, one per head commit |
| `audit/pr-<pr-id>.jsonl` | Append-only audit log: identifiers and statuses, never code |

Useful options:
- `--reports-dir <dir>` reuses existing reports (one sub-folder per check) instead of running Maven.
- `--check sast=PASSED` overrides one check result.
- `--keep-worktree` keeps the worktree for debugging.
- `--help` lists everything.

The exit code is 0 whenever the analysis completes. Readiness is reported in the output, not through the exit code.

## Guardrails

- A missing, errored or still-running check never counts as passed.
- The engine never merges, approves or changes branch protection.
- Findings are only CONFIRMED with independent evidence: a tool, a test, a contract or a coverage report.
