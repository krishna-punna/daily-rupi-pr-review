package com.dailyrupi.prreview.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.checks.CheckResult.Status;
import com.dailyrupi.prreview.checks.ReportBasedChecks;
import com.dailyrupi.prreview.diff.FileDiff;
import com.dailyrupi.prreview.diff.FileDiff.ChangeType;
import com.dailyrupi.prreview.diff.Hunk;
import com.dailyrupi.prreview.findings.Category;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.FindingStatus;
import com.dailyrupi.prreview.policy.PolicyLoader;
import com.dailyrupi.prreview.policy.ReviewPolicy;
import com.dailyrupi.prreview.publish.ReviewReport;
import com.dailyrupi.prreview.readiness.ReadinessStatus;
import com.dailyrupi.prreview.source.PullRequestSnapshot;
import com.dailyrupi.prreview.tools.Fixtures;

import org.junit.jupiter.api.Test;

/** The whole deterministic analysis, driven by real reports from backend/. */
class ReviewAnalyzerTest {

    private static final Instant NOW = Instant.parse("2026-10-15T06:00:00Z");
    private static final String HEAD = "d03fff4aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static FileDiff file(String path, ChangeType type, Integer... lines) {
        return new FileDiff(type == ChangeType.ADDED ? null : path, path, type, false,
                List.of(new Hunk(1, 1, 1, lines.length, List.of(), new TreeSet<>(List.of(lines)))));
    }

    private static PullRequestSnapshot pr(FileDiff... files) {
        return new PullRequestSnapshot("daily-rupi-pr-review", "local-test", "scratch into main", "main",
                "34e0e9daaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "scratch", HEAD, "34e0e9daaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                List.of(files));
    }

    private static ReviewPolicy policy() throws IOException {
        return new PolicyLoader().load(Path.of("review-policy.yml"));
    }

    /** The scratch branch: a new class that concatenates SQL, and a renamed field in ExpenseDtos. */
    private static PullRequestSnapshot defectivePr() {
        return pr(file("backend/src/main/java/com/dailyrupi/expense/ScratchSearch.java", ChangeType.ADDED,
                        IntStream.rangeClosed(1, 21).boxed().toArray(Integer[]::new)),
                file("backend/src/main/java/com/dailyrupi/expense/ExpenseDtos.java", ChangeType.MODIFIED, 37));
    }

    @Test
    void defectiveBranchIsNotReadyFromDeterministicEvidenceAlone() throws IOException {
        Path reports = Fixtures.report("defective");
        Map<String, CheckResult> checks = new ReportBasedChecks(clock).infer(reports, policy().requiredChecks());

        ReviewReport report = new ReviewAnalyzer(clock).analyze("an-test", defectivePr(), policy(), reports, checks,
                HEAD, null, NOW);

        assertThat(checks.values()).extracting(CheckResult::status).containsOnly(Status.FAILED);
        assertThat(checks.get("backend-tests").detail())
                .isEqualTo("1 of 35 tests failed: ExpenseAndStatusTest.expenseCanBeAddedEditedListedAndDeleted");
        assertThat(checks.get("sast").detail())
                .isEqualTo("1 security issue: SQL_INJECTION_SPRING_JDBC at com/dailyrupi/expense/ScratchSearch.java:19");

        assertThat(report.readiness().status()).isEqualTo(ReadinessStatus.NOT_READY);
        assertThat(report.readiness().rulesFired()).containsExactly("R2", "R5", "R7");
        assertThat(report.findings()).extracting(Finding::findingId, Finding::category).containsExactly(
                org.assertj.core.groups.Tuple.tuple("F-001", Category.SECURITY_SQL_INJECTION),
                org.assertj.core.groups.Tuple.tuple("F-002", Category.API_COMPATIBILITY_BREAKING_CHANGE),
                org.assertj.core.groups.Tuple.tuple("F-003", Category.API_COMPATIBILITY_BREAKING_CHANGE),
                org.assertj.core.groups.Tuple.tuple("F-004", Category.API_COMPATIBILITY_BREAKING_CHANGE));
        assertThat(report.findings()).extracting(Finding::status).containsOnly(FindingStatus.CONFIRMED);
        assertThat(report.diffCoverage().ratio()).isNull();
        assertThat(report.readiness().uncertainties()).anyMatch(u -> u.startsWith("No coverage report"));
        assertThat(report.aiStatusReason()).contains("Phase 3");
    }

    @Test
    void missingReportsForRequiredChecksMeanNotReady() throws IOException {
        // The "clean" fixture only has the backend-tests reports.
        Path reports = Fixtures.report("clean");
        Map<String, CheckResult> checks = new ReportBasedChecks(clock).infer(reports, policy().requiredChecks());
        PullRequestSnapshot pr = pr(file("backend/src/main/java/com/dailyrupi/admin/AdminController.java",
                ChangeType.MODIFIED, 31, 32));

        ReviewReport report = new ReviewAnalyzer(clock).analyze("an-test", pr, policy(), reports, checks, HEAD, null, NOW);

        assertThat(checks.get("backend-tests").status()).isEqualTo(Status.PASSED);
        assertThat(checks.get("contract-tests").status()).isEqualTo(Status.MISSING);
        assertThat(checks.get("sast").status()).isEqualTo(Status.MISSING);
        assertThat(report.readiness().status()).isEqualTo(ReadinessStatus.NOT_READY);
        assertThat(report.readiness().rulesFired()).contains("R3");
        assertThat(report.diffCoverage().ratio()).isEqualTo(1.0);
    }

    @Test
    void cleanChangeWithEveryCheckPassedWaitsOnlyForHumanApproval() throws IOException {
        Path reports = Fixtures.report("clean");
        Map<String, CheckResult> checks = new LinkedHashMap<>();
        for (String name : policy().requiredChecks()) {
            checks.put(name, new CheckResult(name, Status.PASSED, "test", null, NOW));
        }
        PullRequestSnapshot pr = pr(file("backend/src/main/java/com/dailyrupi/admin/AdminController.java",
                ChangeType.MODIFIED, 31, 32));

        ReviewReport report = new ReviewAnalyzer(clock).analyze("an-test", pr, policy(), reports, checks, HEAD, null, NOW);

        assertThat(report.findings()).isEmpty();
        assertThat(report.readiness().status()).isEqualTo(ReadinessStatus.READY_FOR_HUMAN_APPROVAL);
        assertThat(report.readiness().rulesFired()).containsExactly("R7");
        assertThat(report.readiness().requiredHumanApprovalsRemaining()).isEqualTo(1);
    }

    @Test
    void analysisOfAnOlderCommitIsStale() throws IOException {
        Path reports = Fixtures.report("defective");
        Map<String, CheckResult> checks = new ReportBasedChecks(clock).infer(reports, policy().requiredChecks());

        ReviewReport report = new ReviewAnalyzer(clock).analyze("an-test", defectivePr(), policy(), reports, checks,
                "ffffffffffffffffffffffffffffffffffffffff", null, NOW);

        assertThat(report.readiness().rulesFired()).contains("R1");
        assertThat(report.readiness().blockingReasons().get(0)).startsWith("Analysis is for d03fff4 but the PR head is now fffffff");
    }
}
