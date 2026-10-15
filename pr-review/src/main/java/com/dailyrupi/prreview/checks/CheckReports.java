package com.dailyrupi.prreview.checks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.dailyrupi.prreview.tools.CoverageReport;
import com.dailyrupi.prreview.tools.JacocoReportParser;
import com.dailyrupi.prreview.tools.SastIssue;
import com.dailyrupi.prreview.tools.SpotBugsReportParser;
import com.dailyrupi.prreview.tools.SurefireReportParser;
import com.dailyrupi.prreview.tools.TestCaseResult;

/**
 * The reports one check left behind, in {@code <reports root>/<check name>/}. Files are found anywhere below
 * that folder, so both the local layout and a downloaded CI artifact (which keeps deeper paths) work.
 */
public final class CheckReports {

    private static final int MAX_DEPTH = 8;

    private final Path dir;

    public CheckReports(Path dir) {
        this.dir = dir;
    }

    public boolean exists() {
        return Files.isDirectory(dir);
    }

    /** All test cases from every TEST-*.xml below the folder; empty when there are none. */
    public Optional<List<TestCaseResult>> tests() throws IOException {
        List<Path> reportDirs = find(p -> p.getFileName().toString().startsWith("TEST-")
                && p.getFileName().toString().endsWith(".xml")).stream().map(Path::getParent).distinct().sorted().toList();
        if (reportDirs.isEmpty()) {
            return Optional.empty();
        }
        SurefireReportParser parser = new SurefireReportParser();
        List<TestCaseResult> cases = new ArrayList<>();
        for (Path reportDir : reportDirs) {
            parser.parseDirectory(reportDir).ifPresent(cases::addAll);
        }
        return Optional.of(cases);
    }

    public Optional<List<SastIssue>> sast() throws IOException {
        Optional<Path> report = find(p -> p.getFileName().toString().equals("spotbugsXml.xml")).stream().findFirst();
        return report.isEmpty() ? Optional.empty() : Optional.of(new SpotBugsReportParser().parse(report.get()));
    }

    public Optional<CoverageReport> coverage() throws IOException {
        Optional<Path> report = find(p -> p.getFileName().toString().equals("jacoco.xml")).stream().findFirst();
        return report.isEmpty() ? Optional.empty() : Optional.of(new JacocoReportParser().parse(report.get()));
    }

    private List<Path> find(java.util.function.Predicate<Path> match) throws IOException {
        if (!exists()) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir, MAX_DEPTH)) {
            return walk.filter(Files::isRegularFile).filter(match).sorted().toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
