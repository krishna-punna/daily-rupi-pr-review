package com.dailyrupi.prreview.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.SortedSet;
import java.util.TreeSet;

import com.dailyrupi.prreview.diff.ChangedLines;
import com.dailyrupi.prreview.tools.CoverageReport.LineCoverage;

/**
 * Coverage of the lines a PR changes, not of the whole code base: of the changed lines that hold code
 * (comments, blank lines and declarations hold none), how many did at least one test execute.
 */
public record DiffCoverage(int coverableLines, int coveredLines, List<FileCoverage> files) {

    public record FileCoverage(String file, int coverable, int covered, SortedSet<Integer> uncoveredLines) {
    }

    public DiffCoverage {
        files = List.copyOf(files);
    }

    /** Empty when no changed line holds code, so there is nothing to measure. */
    public OptionalDouble ratio() {
        return coverableLines == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) coveredLines / coverableLines);
    }

    /**
     * @param sourceRoot repository-relative directory the report's paths are relative to, e.g. backend/src/main/java
     */
    public static DiffCoverage compute(ChangedLines changed, CoverageReport report, String sourceRoot) {
        String prefix = sourceRoot.replace('\\', '/').replaceAll("/+$", "") + "/";
        List<FileCoverage> files = new ArrayList<>();
        int coverable = 0;
        int covered = 0;
        for (String file : changed.files()) {
            if (!file.startsWith(prefix) || !file.endsWith(".java")) {
                continue;
            }
            Map<Integer, LineCoverage> lines = report.lines(file.substring(prefix.length()));
            int fileCoverable = 0;
            int fileCovered = 0;
            SortedSet<Integer> uncovered = new TreeSet<>();
            for (int line : changed.lines(file)) {
                LineCoverage coverage = lines.get(line);
                if (coverage == null) {
                    continue;
                }
                fileCoverable++;
                if (coverage.covered()) {
                    fileCovered++;
                } else {
                    uncovered.add(line);
                }
            }
            if (fileCoverable > 0) {
                files.add(new FileCoverage(file, fileCoverable, fileCovered, uncovered));
                coverable += fileCoverable;
                covered += fileCovered;
            }
        }
        return new DiffCoverage(coverable, covered, files);
    }
}
