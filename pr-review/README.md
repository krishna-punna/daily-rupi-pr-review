# PR review engine (proof of concept)

An AI pull-request review and merge-readiness engine. It reviews pull requests against the Daily Rupi backend (`../backend`).

The AI's findings are only advice. Deterministic checks (tests, contract tests, SAST, coverage) and human approval decide whether a PR is ready to merge.

**Status:** skeleton (Phase 0). See `../docs/pr-review/PROGRESS.md` for progress and `../docs/pr-review/POC_SPEC.md` for the full specification.

## Build and run

```
mvn -f pr-review/pom.xml package
java -jar pr-review/target/pr-review.jar --version
```

Java 17 or newer. No database, Docker, or API key is needed to build.
