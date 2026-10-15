package com.dailyrupi.prreview.tools;

import static com.dailyrupi.prreview.tools.XmlDocuments.attr;
import static com.dailyrupi.prreview.tools.XmlDocuments.child;
import static com.dailyrupi.prreview.tools.XmlDocuments.childText;
import static com.dailyrupi.prreview.tools.XmlDocuments.children;
import static com.dailyrupi.prreview.tools.XmlDocuments.intAttr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Element;

/** Reads spotbugsXml.xml (SpotBugs 4.x with FindSecBugs). */
public final class SpotBugsReportParser {

    public List<SastIssue> parse(Path report) throws IOException {
        Element root = XmlDocuments.parse(report).getDocumentElement();
        List<SastIssue> issues = new ArrayList<>();
        for (Element bug : children(root, "BugInstance")) {
            Optional<Element> method = primary(children(bug, "Method"));
            Element line = primary(children(bug, "SourceLine"))
                    .or(() -> method.flatMap(m -> child(m, "SourceLine")))
                    .or(() -> child(bug, "Class").flatMap(c -> child(c, "SourceLine")))
                    .orElse(null);
            issues.add(new SastIssue(
                    attr(bug, "type"),
                    attr(bug, "category"),
                    orDefault(intAttr(bug, "priority"), 3),
                    orDefault(intAttr(bug, "rank"), 20),
                    intAttr(bug, "cweid"),
                    method.map(m -> attr(m, "classname"))
                            .orElseGet(() -> child(bug, "Class").map(c -> attr(c, "classname")).orElse(null)),
                    method.map(m -> attr(m, "name")).orElse(null),
                    method.map(m -> attr(m, "signature")).orElse(null),
                    line == null ? null : attr(line, "sourcepath"),
                    line == null ? null : intAttr(line, "start"),
                    line == null ? null : intAttr(line, "end"),
                    childText(bug, "ShortMessage"),
                    childText(bug, "LongMessage")));
        }
        return issues;
    }

    /** The element marked primary='true', else the first one. */
    private static Optional<Element> primary(List<Element> elements) {
        return elements.stream().filter(e -> "true".equals(attr(e, "primary"))).findFirst()
                .or(() -> elements.stream().findFirst());
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
