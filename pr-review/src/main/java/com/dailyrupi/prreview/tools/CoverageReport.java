package com.dailyrupi.prreview.tools;

import java.util.Map;

/**
 * Line coverage per source file, keyed by the path under the source root (com/dailyrupi/admin/AdminController.java).
 * A line is present only if it holds bytecode, so "present" means "coverable".
 */
public record CoverageReport(Map<String, Map<Integer, LineCoverage>> files) {

    public record LineCoverage(int missedInstructions, int coveredInstructions) {

        public boolean covered() {
            return coveredInstructions > 0;
        }
    }

    public CoverageReport {
        files = Map.copyOf(files);
    }

    public Map<Integer, LineCoverage> lines(String relativePath) {
        return files.getOrDefault(relativePath.replace('\\', '/'), Map.of());
    }
}
