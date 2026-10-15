package com.dailyrupi.prreview.tools;

import static com.dailyrupi.prreview.tools.XmlDocuments.attr;
import static com.dailyrupi.prreview.tools.XmlDocuments.children;
import static com.dailyrupi.prreview.tools.XmlDocuments.intAttr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.dailyrupi.prreview.tools.CoverageReport.LineCoverage;

import org.w3c.dom.Element;

/** Reads JaCoCo's jacoco.xml: report / package / sourcefile / line. */
public final class JacocoReportParser {

    public CoverageReport parse(Path report) throws IOException {
        Element root = XmlDocuments.parse(report).getDocumentElement();
        Map<String, Map<Integer, LineCoverage>> files = new HashMap<>();
        for (Element pkg : children(root, "package")) {
            String packagePath = attr(pkg, "name");
            for (Element sourceFile : children(pkg, "sourcefile")) {
                Map<Integer, LineCoverage> lines = new TreeMap<>();
                for (Element line : children(sourceFile, "line")) {
                    Integer nr = intAttr(line, "nr");
                    if (nr != null) {
                        lines.put(nr, new LineCoverage(zeroIfNull(intAttr(line, "mi")), zeroIfNull(intAttr(line, "ci"))));
                    }
                }
                String name = attr(sourceFile, "name");
                files.put(packagePath == null || packagePath.isEmpty() ? name : packagePath + "/" + name, lines);
            }
        }
        return new CoverageReport(files);
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
