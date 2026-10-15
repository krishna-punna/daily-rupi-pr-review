package com.dailyrupi.prreview.checks;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dailyrupi.prreview.checks.CheckResult.Status;
import com.dailyrupi.prreview.tools.SastIssue;
import com.dailyrupi.prreview.tools.TestCaseResult;

/**
 * Check results read from reports someone else produced (a CI artifact, an earlier run), when the authoritative
 * pass/fail is not available. A folder with no readable report is MISSING, never PASSED.
 */
public final class ReportBasedChecks {

    private final Clock clock;

    public ReportBasedChecks(Clock clock) {
        this.clock = clock;
    }

    public Map<String, CheckResult> infer(Path reportsRoot, List<String> checkNames) throws IOException {
        Map<String, CheckResult> results = new LinkedHashMap<>();
        for (String name : checkNames) {
            CheckReports reports = new CheckReports(reportsRoot.resolve(name));
            Optional<List<TestCaseResult>> tests = reports.tests();
            Optional<List<SastIssue>> sast = reports.sast();
            Status status;
            if (tests.isPresent()) {
                status = tests.get().stream().anyMatch(TestCaseResult::failed) ? Status.FAILED : Status.PASSED;
            } else if (sast.isPresent()) {
                status = sast.get().isEmpty() ? Status.PASSED : Status.FAILED;
            } else {
                status = Status.MISSING;
            }
            String detail = status == Status.MISSING ? "no reports in " + reportsRoot.relativize(reportsRoot.resolve(name))
                    : CheckDetails.describe(reports);
            results.put(name, new CheckResult(name, status, "reports directory (inferred)", detail, clock.instant()));
        }
        return results;
    }
}
