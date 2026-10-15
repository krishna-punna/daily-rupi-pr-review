package com.dailyrupi.prreview.publish;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.diff.FileDiff.ChangeType;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.readiness.AiStatus;
import com.dailyrupi.prreview.readiness.ReadinessReport;

/**
 * One analysis of one PR at one head commit (spec §8.2): what was examined, the checks, the findings and the
 * readiness decision. Written as review-report.json and rendered as review-report.md.
 */
public record ReviewReport(
        int schemaVersion,
        String analysisId,
        String repository,
        String prId,
        String title,
        String baseRef,
        String baseSha,
        String headRef,
        String headSha,
        String diffBaseSha,
        Instant startedAt,
        Instant finishedAt,
        String policyVersion,
        Map<String, String> promptVersions,
        String model,
        AiStatus aiStatus,
        String aiStatusReason,
        List<ChangedFile> changedFiles,
        List<CheckResult> checks,
        Coverage diffCoverage,
        List<Finding> findings,
        ReadinessReport readiness) {

    public static final int SCHEMA_VERSION = 1;

    public record ChangedFile(String path, ChangeType changeType, int addedLines) {
    }

    /** {@code ratio} is null when nothing coverable changed or no coverage report exists. */
    public record Coverage(int coverableLines, int coveredLines, Double ratio, String note) {
    }

    public ReviewReport {
        promptVersions = promptVersions == null ? Map.of() : Map.copyOf(promptVersions);
        changedFiles = List.copyOf(changedFiles);
        checks = List.copyOf(checks);
        findings = List.copyOf(findings);
    }

    public String shortHeadSha() {
        return headSha.substring(0, Math.min(7, headSha.length()));
    }
}
