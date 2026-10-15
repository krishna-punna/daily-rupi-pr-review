package com.dailyrupi.prreview.tools;

import static com.dailyrupi.prreview.tools.XmlDocuments.attr;
import static com.dailyrupi.prreview.tools.XmlDocuments.child;
import static com.dailyrupi.prreview.tools.XmlDocuments.children;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.dailyrupi.prreview.tools.TestCaseResult.Outcome;

import org.w3c.dom.Element;

/** Reads every TEST-*.xml file in a surefire-reports directory. */
public final class SurefireReportParser {

    /** Empty when the directory is missing or holds no reports: the tests did not run, which is not a pass. */
    public Optional<List<TestCaseResult>> parseDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("TEST-") && name.endsWith(".xml");
            }).sorted().toList();
        }
        if (files.isEmpty()) {
            return Optional.empty();
        }
        List<TestCaseResult> cases = new ArrayList<>();
        for (Path file : files) {
            cases.addAll(parseFile(file));
        }
        return Optional.of(cases);
    }

    public List<TestCaseResult> parseFile(Path file) throws IOException {
        Element suite = XmlDocuments.parse(file).getDocumentElement();
        List<TestCaseResult> cases = new ArrayList<>();
        for (Element testCase : children(suite, "testcase")) {
            Optional<Element> failure = child(testCase, "failure");
            Optional<Element> error = child(testCase, "error");
            Outcome outcome = failure.isPresent() ? Outcome.FAILED
                    : error.isPresent() ? Outcome.ERROR
                    : child(testCase, "skipped").isPresent() ? Outcome.SKIPPED
                    : Outcome.PASSED;
            Element problem = failure.or(() -> error).orElse(null);
            cases.add(new TestCaseResult(
                    attr(testCase, "classname"),
                    attr(testCase, "name"),
                    outcome,
                    problem == null ? null : attr(problem, "type"),
                    problem == null ? null : attr(problem, "message")));
        }
        return cases;
    }
}
