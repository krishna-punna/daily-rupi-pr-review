package com.dailyrupi.prreview.checks;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.dailyrupi.prreview.tools.SastIssue;
import com.dailyrupi.prreview.tools.TestCaseResult;

/** One-line explanations of a check's result, taken from its reports ("1 of 35 tests failed: ..."). */
public final class CheckDetails {

    private static final int MAX_LISTED = 5;

    private CheckDetails() {
    }

    public static String describe(CheckReports reports) throws IOException {
        Optional<List<TestCaseResult>> tests = reports.tests();
        Optional<List<SastIssue>> sast = reports.sast();
        if (tests.isPresent()) {
            List<TestCaseResult> failed = tests.get().stream().filter(TestCaseResult::failed).toList();
            if (failed.isEmpty()) {
                return tests.get().size() + " tests passed";
            }
            return failed.size() + " of " + tests.get().size() + " tests failed: " + list(failed.stream()
                    .map(t -> t.className() != null && t.className().endsWith("ConsumerContractTest")
                            ? contractName(t.name()) : t.displayName())
                    .toList());
        }
        if (sast.isPresent()) {
            if (sast.get().isEmpty()) {
                return "no security issues";
            }
            return sast.get().size() + " security issue" + (sast.get().size() == 1 ? "" : "s") + ": " + list(sast.get()
                    .stream().map(i -> i.type() + " at " + i.sourcePath() + (i.startLine() == null ? "" : ":" + i.startLine()))
                    .toList());
        }
        return null;
    }

    private static String contractName(String name) {
        int split = name.indexOf(") ");
        return split >= 0 ? name.substring(split + 2) : name;
    }

    private static String list(List<String> items) {
        String shown = items.stream().limit(MAX_LISTED).collect(Collectors.joining(", "));
        return items.size() > MAX_LISTED ? shown + " and " + (items.size() - MAX_LISTED) + " more" : shown;
    }
}
