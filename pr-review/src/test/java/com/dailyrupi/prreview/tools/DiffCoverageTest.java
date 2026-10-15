package com.dailyrupi.prreview.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;

import com.dailyrupi.prreview.diff.ChangedLines;
import com.dailyrupi.prreview.diff.FileDiff;
import com.dailyrupi.prreview.diff.FileDiff.ChangeType;
import com.dailyrupi.prreview.diff.Hunk;

import org.junit.jupiter.api.Test;

class DiffCoverageTest {

    private static final String ROOT = "backend/src/main/java";

    private static FileDiff changed(String path, Integer... lines) {
        return new FileDiff(path, path, ChangeType.MODIFIED, false,
                List.of(new Hunk(1, 1, 1, 1, List.of(), new TreeSet<>(List.of(lines)))));
    }

    @Test
    void countsOnlyChangedLinesThatHoldCodeUnderTheSourceRoot() throws IOException {
        CoverageReport report = new JacocoReportParser().parse(Fixtures.report("clean/backend-tests/jacoco.xml"));
        ChangedLines changed = ChangedLines.of(List.of(
                // 29 is the method signature (no bytecode), 31 and 32 are covered statements.
                changed(ROOT + "/com/dailyrupi/admin/AdminController.java", 29, 31, 32),
                // Three statements no test reaches.
                changed(ROOT + "/com/dailyrupi/common/GlobalExceptionHandler.java", 38, 44, 45),
                // Outside the source root: not part of the measure.
                changed("docs/notes.md", 1, 2)));

        DiffCoverage coverage = DiffCoverage.compute(changed, report, ROOT);

        assertThat(coverage.coverableLines()).isEqualTo(5);
        assertThat(coverage.coveredLines()).isEqualTo(2);
        assertThat(coverage.ratio()).hasValue(0.4);
        assertThat(coverage.files()).filteredOn(f -> f.file().endsWith("GlobalExceptionHandler.java")).singleElement()
                .satisfies(f -> assertThat(f.uncoveredLines()).containsExactly(38, 44, 45));
    }

    @Test
    void nothingCoverableChangedMeansNoRatio() throws IOException {
        CoverageReport report = new JacocoReportParser().parse(Fixtures.report("clean/backend-tests/jacoco.xml"));
        ChangedLines changed = ChangedLines.of(List.of(changed(ROOT + "/com/dailyrupi/admin/AdminController.java", 29)));

        assertThat(DiffCoverage.compute(changed, report, ROOT).ratio()).isEmpty();
    }
}
