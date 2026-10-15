package com.dailyrupi.prreview.readiness;

import static com.dailyrupi.prreview.readiness.ReadinessStatus.ELIGIBLE_FOR_MERGE;
import static com.dailyrupi.prreview.readiness.ReadinessStatus.NEEDS_INVESTIGATION;
import static com.dailyrupi.prreview.readiness.ReadinessStatus.NOT_READY;
import static com.dailyrupi.prreview.readiness.ReadinessStatus.READY_FOR_HUMAN_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.checks.CheckResult.Status;
import com.dailyrupi.prreview.findings.Category;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.FindingSource;
import com.dailyrupi.prreview.findings.FindingStatus;
import com.dailyrupi.prreview.findings.Severity;
import com.dailyrupi.prreview.policy.ReviewPolicy;
import com.dailyrupi.prreview.policy.ReviewPolicy.AiReviewTrigger;
import com.dailyrupi.prreview.policy.ReviewPolicy.Enforcement;
import com.dailyrupi.prreview.policy.ReviewPolicy.ProjectConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One row per rule (R1–R8, W1–W5), plus precedence and "never blocks" rows. Every row starts from a PR that is
 * eligible to merge and changes exactly what the row is about. None of these inputs are demo scenarios.
 */
class ReadinessEngineTest {

    private static final Instant NOW = Instant.parse("2026-10-15T06:00:00Z");
    private static final List<String> REQUIRED = List.of("backend-tests", "contract-tests", "sast");

    /** A mutable starting point: head unchanged, all checks passed, one approval, AI ran, good coverage. */
    static final class Case {
        String analysedHead = "aaaaaaa1111";
        String currentHead = "aaaaaaa1111";
        Map<String, CheckResult> checks = new LinkedHashMap<>();
        List<Finding> findings = new ArrayList<>();
        Integer approvals = 1;
        Boolean conflict = false;
        AiStatus ai = AiStatus.OK;
        Double coverage = 0.9;
        List<String> testsNotRun = new ArrayList<>();
        boolean requireAi = false;

        Case() {
            REQUIRED.forEach(name -> check(name, Status.PASSED));
        }

        Case check(String name, Status status) {
            checks.put(name, new CheckResult(name, status, "test", status == Status.FAILED ? "2 tests failed" : null, NOW));
            return this;
        }

        Case finding(FindingStatus status, Severity severity) {
            findings.add(Finding.builder().findingId("F-" + (findings.size() + 1)).category(Category.CORRECTNESS_OTHER)
                    .severity(severity).title("an issue").file("A.java").lines(3, 3).status(status)
                    .source(new FindingSource(FindingSource.Type.SAST, "findsecbugs", "RULE", null)).build());
            return this;
        }

        ReadinessInput input() {
            return new ReadinessInput(analysedHead, currentHead, checks, findings, approvals, conflict, ai, coverage,
                    testsNotRun, List.of());
        }

        ReviewPolicy policy() {
            return new ReviewPolicy("1.0.0-test", List.of(), REQUIRED, Severity.HIGH, Severity.HIGH, 0.60, 1, requireAi,
                    Enforcement.ADVISORY, new AiReviewTrigger(List.of(), null),
                    new ProjectConfig("backend", "backend/src/main/java", "C", "d", 3, Map.of()));
        }
    }

    private static Arguments row(String name, Consumer<Case> change, ReadinessStatus expected, String... rules) {
        return Arguments.of(name, change, expected, List.of(rules));
    }

    static Stream<Arguments> rules() {
        return Stream.of(
                row("R8: every condition met", c -> { }, ELIGIBLE_FOR_MERGE, "R8"),
                row("R1: PR head moved since the analysis", c -> c.currentHead = "bbbbbbb2222", NOT_READY, "R1"),
                row("R1: PR head unknown", c -> c.currentHead = null, NOT_READY, "R1"),
                row("R2: a required check failed", c -> c.check("backend-tests", Status.FAILED), NOT_READY, "R2"),
                row("R3: a required check never reported", c -> c.checks.remove("sast"), NOT_READY, "R3"),
                row("R3: a required check is MISSING", c -> c.check("sast", Status.MISSING), NOT_READY, "R3"),
                row("R3: a required check errored", c -> c.check("contract-tests", Status.ERROR), NOT_READY, "R3"),
                row("R3: a required check is still running", c -> c.check("contract-tests", Status.RUNNING), NOT_READY, "R3"),
                row("Only required checks count", c -> c.check("lint", Status.FAILED), ELIGIBLE_FOR_MERGE, "R8"),
                row("R4: merge conflict", c -> c.conflict = true, NOT_READY, "R4"),
                row("Merge conflicts not checked do not block", c -> c.conflict = null, ELIGIBLE_FOR_MERGE, "R8"),
                row("R5: confirmed HIGH finding", c -> c.finding(FindingStatus.CONFIRMED, Severity.HIGH), NOT_READY, "R5"),
                row("R5: unresolved CRITICAL finding", c -> c.finding(FindingStatus.UNRESOLVED, Severity.CRITICAL), NOT_READY, "R5"),
                row("R5: regressed HIGH finding", c -> c.finding(FindingStatus.REGRESSED, Severity.HIGH), NOT_READY, "R5"),
                row("W5: confirmed below the blocking severity", c -> c.finding(FindingStatus.CONFIRMED, Severity.MEDIUM),
                        ELIGIBLE_FOR_MERGE, "R8", "W5"),
                row("W5: unresolved below the blocking severity", c -> c.finding(FindingStatus.UNRESOLVED, Severity.LOW),
                        ELIGIBLE_FOR_MERGE, "R8", "W5"),
                row("R6: severe AI-only finding needs investigation", c -> c.finding(FindingStatus.NEEDS_REVIEW, Severity.HIGH),
                        NEEDS_INVESTIGATION, "R6"),
                row("AI-only CRITICAL finding still never blocks", c -> c.finding(FindingStatus.NEEDS_REVIEW, Severity.CRITICAL),
                        NEEDS_INVESTIGATION, "R6"),
                row("W1: minor AI-only finding", c -> c.finding(FindingStatus.NEEDS_REVIEW, Severity.LOW),
                        ELIGIBLE_FOR_MERGE, "R8", "W1"),
                row("R6: AI review required by policy but unavailable", c -> {
                    c.requireAi = true;
                    c.ai = AiStatus.UNAVAILABLE;
                }, NEEDS_INVESTIGATION, "R6"),
                row("AI review not required and skipped", c -> c.ai = AiStatus.SKIPPED, ELIGIBLE_FOR_MERGE, "R8"),
                row("R7: required approval missing", c -> c.approvals = 0, READY_FOR_HUMAN_APPROVAL, "R7"),
                row("R7: approvals unknown count as missing", c -> c.approvals = null, READY_FOR_HUMAN_APPROVAL, "R7"),
                row("W2: low diff coverage", c -> c.coverage = 0.4, ELIGIBLE_FOR_MERGE, "R8", "W2"),
                row("Diff coverage not measured adds no warning", c -> c.coverage = null, ELIGIBLE_FOR_MERGE, "R8"),
                row("W3: fix not verified yet", c -> c.finding(FindingStatus.FIX_CANDIDATE, Severity.HIGH),
                        ELIGIBLE_FOR_MERGE, "R8", "W3"),
                row("W4: recommended tests did not run", c -> c.testsNotRun.add("ExpenseSyncTest"),
                        ELIGIBLE_FOR_MERGE, "R8", "W4"),
                row("Pre-existing, rejected, duplicate, fixed and obsolete findings never count", c -> {
                    c.finding(FindingStatus.PRE_EXISTING, Severity.CRITICAL);
                    c.finding(FindingStatus.REJECTED, Severity.HIGH);
                    c.finding(FindingStatus.SUPPRESSED_DUPLICATE, Severity.HIGH);
                    c.finding(FindingStatus.VERIFIED_FIXED, Severity.CRITICAL);
                    c.finding(FindingStatus.OBSOLETE, Severity.HIGH);
                }, ELIGIBLE_FOR_MERGE, "R8"),
                row("NOT_READY outranks investigation and approval", c -> {
                    c.check("sast", Status.FAILED);
                    c.finding(FindingStatus.NEEDS_REVIEW, Severity.HIGH);
                    c.approvals = 0;
                }, NOT_READY, "R2", "R6", "R7"),
                row("Investigation outranks approval", c -> {
                    c.finding(FindingStatus.NEEDS_REVIEW, Severity.HIGH);
                    c.approvals = 0;
                }, NEEDS_INVESTIGATION, "R6", "R7"),
                row("Several blockers are all reported", c -> {
                    c.currentHead = "ccccccc3333";
                    c.check("backend-tests", Status.FAILED);
                    c.checks.remove("sast");
                    c.conflict = true;
                    c.finding(FindingStatus.CONFIRMED, Severity.HIGH);
                }, NOT_READY, "R1", "R2", "R3", "R4", "R5"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void readinessFollowsTheRules(String name, Consumer<Case> change, ReadinessStatus expected, List<String> rules) {
        Case c = new Case();
        change.accept(c);

        ReadinessReport report = new ReadinessEngine(Clock.fixed(NOW, ZoneOffset.UTC)).evaluate(c.input(), c.policy());

        assertThat(report.status()).as(name).isEqualTo(expected);
        assertThat(report.rulesFired()).as(name).containsExactlyElementsOf(rules);
    }

    @Test
    void reasonsNameTheCheckTheFindingAndItsEvidence() {
        Case c = new Case().check("backend-tests", Status.FAILED).finding(FindingStatus.CONFIRMED, Severity.HIGH);
        c.checks.remove("sast");

        ReadinessReport report = new ReadinessEngine(Clock.fixed(NOW, ZoneOffset.UTC)).evaluate(c.input(), c.policy());

        assertThat(report.blockingReasons()).containsExactly(
                "Required check `backend-tests` failed: 2 tests failed",
                "Required check `sast` is missing (a missing check never counts as passed)",
                "F-1 CORRECTNESS/OTHER HIGH is CONFIRMED: an issue (A.java:3) [evidence: findsecbugs RULE]");
    }

    @Test
    void reportCarriesPolicyVersionTimeAndApprovalsStillNeeded() {
        Case c = new Case();
        c.approvals = null;
        c.ai = AiStatus.SKIPPED;

        ReadinessReport report = new ReadinessEngine(Clock.fixed(NOW, ZoneOffset.UTC)).evaluate(c.input(), c.policy());

        assertThat(report.policyVersion()).isEqualTo("1.0.0-test");
        assertThat(report.evaluatedAt()).isEqualTo(NOW);
        assertThat(report.requiredHumanApprovalsRemaining()).isEqualTo(1);
        assertThat(report.uncertainties()).anyMatch(u -> u.startsWith("AI review did not run (SKIPPED)"))
                .anyMatch(u -> u.startsWith("Approvals are unknown"));
    }
}
