package com.dailyrupi.prreview.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dailyrupi.prreview.checks.CheckReports;
import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.diff.ChangedLines;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.ToolFindingNormalizer;
import com.dailyrupi.prreview.policy.ReviewPolicy;
import com.dailyrupi.prreview.publish.ReviewReport;
import com.dailyrupi.prreview.readiness.AiStatus;
import com.dailyrupi.prreview.readiness.ReadinessEngine;
import com.dailyrupi.prreview.readiness.ReadinessInput;
import com.dailyrupi.prreview.readiness.ReadinessReport;
import com.dailyrupi.prreview.source.PullRequestSnapshot;
import com.dailyrupi.prreview.tools.CoverageReport;
import com.dailyrupi.prreview.tools.DiffCoverage;

/**
 * The deterministic analysis: reads the check reports, turns them into findings, measures diff coverage and
 * applies the readiness rules. No model is involved; AI review joins in Phase 3.
 */
public final class ReviewAnalyzer {

    static final String AI_NOT_BUILT = "AI review is not part of the engine yet (added in Phase 3)";

    private final Clock clock;

    public ReviewAnalyzer(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param reportsRoot   folder with one sub-folder of reports per check
     * @param checks        authoritative check results
     * @param currentHead   the commit the PR points at now (for the stale-analysis rule)
     * @param approvalsGiven approving reviews, or null when unknown
     */
    public ReviewReport analyze(String analysisId, PullRequestSnapshot pr, ReviewPolicy policy, Path reportsRoot,
            Map<String, CheckResult> checks, String currentHead, Integer approvalsGiven, Instant startedAt)
            throws IOException {
        ToolFindingNormalizer normalizer = new ToolFindingNormalizer(policy.project());
        ChangedLines changed = pr.changedLines();
        List<Finding> findings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        DiffCoverage coverage = null;

        for (String check : checks.keySet()) {
            CheckReports reports = new CheckReports(reportsRoot.resolve(check));
            reports.sast().ifPresent(issues -> findings.addAll(normalizer.fromSast(issues, changed, pr.headSha())));
            reports.tests().ifPresent(cases -> findings.addAll(normalizer.fromContractTests(cases, pr.headSha())));
            Optional<CoverageReport> coverageReport = reports.coverage();
            if (coverageReport.isPresent() && coverage == null) {
                coverage = DiffCoverage.compute(changed, coverageReport.get(), policy.project().sourceRoot());
                findings.addAll(normalizer.fromCoverage(coverage, pr.headSha()));
            }
        }

        String coverageNote = null;
        if (coverage == null) {
            coverageNote = "No coverage report (the backend tests did not complete), so diff coverage is unknown";
            notes.add(coverageNote);
        } else if (coverage.ratio().isEmpty()) {
            coverageNote = "No changed line holds code under " + policy.project().sourceRoot();
        }

        List<Finding> numbered = ToolFindingNormalizer.numbered(dedupe(findings));
        ReadinessReport readiness = new ReadinessEngine(clock).evaluate(new ReadinessInput(pr.headSha(), currentHead,
                checks, numbered, approvalsGiven, null, AiStatus.SKIPPED,
                coverage == null || coverage.ratio().isEmpty() ? null : coverage.ratio().getAsDouble(), List.of(), notes),
                policy);

        return new ReviewReport(ReviewReport.SCHEMA_VERSION, analysisId, pr.repository(), pr.prId(), pr.title(),
                pr.baseRef(), pr.baseSha(), pr.headRef(), pr.headSha(), pr.diffBaseSha(), startedAt, clock.instant(),
                policy.policyVersion(), Map.of(), null, AiStatus.SKIPPED, AI_NOT_BUILT,
                pr.files().stream().map(f -> new ReviewReport.ChangedFile(f.path(), f.changeType(), f.addedLines().size()))
                        .toList(),
                List.copyOf(checks.values()),
                coverage == null ? new ReviewReport.Coverage(0, 0, null, coverageNote)
                        : new ReviewReport.Coverage(coverage.coverableLines(), coverage.coveredLines(),
                                coverage.ratio().isPresent() ? coverage.ratio().getAsDouble() : null, coverageNote),
                numbered, readiness);
    }

    /** The same issue reported through two checks is one finding; the first report wins. */
    private static List<Finding> dedupe(List<Finding> findings) {
        Map<String, Finding> byFingerprint = new LinkedHashMap<>();
        for (Finding finding : findings) {
            byFingerprint.putIfAbsent(finding.fingerprint(), finding);
        }
        return new ArrayList<>(byFingerprint.values());
    }
}
