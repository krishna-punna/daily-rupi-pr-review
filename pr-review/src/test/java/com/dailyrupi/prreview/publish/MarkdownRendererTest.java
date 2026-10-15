package com.dailyrupi.prreview.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.checks.CheckResult.Status;
import com.dailyrupi.prreview.findings.Category;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.FindingSource;
import com.dailyrupi.prreview.findings.FindingStatus;
import com.dailyrupi.prreview.findings.Severity;
import com.dailyrupi.prreview.readiness.AiStatus;
import com.dailyrupi.prreview.readiness.ReadinessReport;
import com.dailyrupi.prreview.readiness.ReadinessStatus;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private static final Instant NOW = Instant.parse("2026-10-15T06:00:00Z");

    private static ReviewReport report(List<Finding> findings) {
        ReadinessReport readiness = new ReadinessReport(ReadinessStatus.NOT_READY,
                List.of("Required check `sast` failed: 1 security issue"), List.of("Tests cover 40% of the changed code"),
                List.of("AI review did not run (SKIPPED); deterministic only"), 1, List.of("R2", "R5", "R7", "W2"),
                "1.0.0", NOW);
        return new ReviewReport(1, "an-1-d03fff4", "repo", "1", "scratch into main", "main", "a".repeat(40),
                "scratch", "d03fff4" + "0".repeat(33), "b".repeat(40), NOW, NOW, "1.0.0", Map.of(), null,
                AiStatus.SKIPPED, "AI review is not part of the engine yet",
                List.of(), List.of(new CheckResult("sast", Status.FAILED, "local", "1 security issue", NOW),
                        new CheckResult("backend-tests", Status.PASSED, "local", "35 tests passed", NOW)),
                new ReviewReport.Coverage(5, 2, 0.4, null), findings, readiness);
    }

    private static Finding finding(String id, FindingStatus status, String title) {
        return Finding.builder().findingId(id).category(Category.SECURITY_SQL_INJECTION).severity(Severity.HIGH)
                .title(title).file("backend/src/main/java/A.java").lines(19, 19).status(status)
                .requirementId("REQ-SEC-001")
                .source(new FindingSource(FindingSource.Type.SAST, "findsecbugs", "SQL_INJECTION_SPRING_JDBC", "CWE-89"))
                .build();
    }

    @Test
    void summaryShowsStatusBlockersChecksAndFindings() {
        String md = new MarkdownRenderer().render(report(List.of(finding("F-001", FindingStatus.CONFIRMED, "SQL injection"))));

        assertThat(md).startsWith("## PR review: ⛔ NOT READY")
                .contains("- Required check `sast` failed: 1 security issue")
                .contains("| `sast` | ❌ FAILED | 1 security issue |")
                .contains("| F-001 | CONFIRMED | HIGH | SECURITY/SQL_INJECTION | SQL injection (REQ-SEC-001) | "
                        + "`backend/src/main/java/A.java:19` | findsecbugs SQL_INJECTION_SPRING_JDBC (CWE-89) |")
                .contains("40% (2 of 5 changed lines with code are executed by tests)")
                .contains("**Human approval:** 1 more approving review required")
                .contains("rules fired: R2, R5, R7, W2");
    }

    @Test
    void preExistingFindingsAreTuckedAwayAndUntrustedTextCannotBreakTheTable() {
        String md = new MarkdownRenderer().render(report(List.of(
                finding("F-001", FindingStatus.PRE_EXISTING, "old | <script>alert(1)</script>\nsecond line"))));

        assertThat(md).contains("None introduced by this change.")
                .contains("<details><summary>Already present before this change (1), not blocking</summary>")
                .contains("old \\| &lt;script&gt;alert(1)&lt;/script&gt; second line")
                .doesNotContain("<script>");
    }

    @Test
    void consoleSummaryIsPlainAscii() {
        String text = new MarkdownRenderer().consoleSummary(report(List.of(finding("F-001", FindingStatus.CONFIRMED, "x"))));

        assertThat(text).startsWith("Readiness: NOT_READY  (rules fired: R2, R5, R7, W2)");
        assertThat(text.chars()).allMatch(c -> c < 128);
    }
}
