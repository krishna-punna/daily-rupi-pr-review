package com.dailyrupi.prreview.readiness;

import java.util.List;
import java.util.Map;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.findings.Finding;

/**
 * Everything the readiness rules look at. Nothing here is computed by an LLM: findings arrive with their status
 * already decided by deterministic validation.
 *
 * @param analysisHeadSha        the commit the analysis examined
 * @param currentHeadSha         the commit the PR points at now
 * @param approvalsGiven         approving reviews, or null when unknown (local runs)
 * @param mergeConflict          true, false, or null when not checked
 * @param diffCoverage           share of changed coverable lines that tests execute, or null when not measured
 * @param recommendedTestsNotRun tests the impact analysis recommends that did not run
 * @param notes                  uncertainties the caller wants on the report (for example "no coverage report")
 */
public record ReadinessInput(
        String analysisHeadSha,
        String currentHeadSha,
        Map<String, CheckResult> checks,
        List<Finding> findings,
        Integer approvalsGiven,
        Boolean mergeConflict,
        AiStatus aiStatus,
        Double diffCoverage,
        List<String> recommendedTestsNotRun,
        List<String> notes) {

    public ReadinessInput {
        java.util.Objects.requireNonNull(analysisHeadSha, "analysisHeadSha");
        java.util.Objects.requireNonNull(aiStatus, "aiStatus");
        checks = Map.copyOf(checks);
        findings = List.copyOf(findings);
        recommendedTestsNotRun = recommendedTestsNotRun == null ? List.of() : List.copyOf(recommendedTestsNotRun);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
