package com.dailyrupi.prreview.diff;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Which lines of which files a PR adds or changes, keyed by repository-relative path with forward slashes. */
public final class ChangedLines {

    private final Map<String, SortedSet<Integer>> byFile;

    private ChangedLines(Map<String, SortedSet<Integer>> byFile) {
        this.byFile = byFile;
    }

    public static ChangedLines of(List<FileDiff> files) {
        Map<String, SortedSet<Integer>> byFile = new LinkedHashMap<>();
        for (FileDiff file : files) {
            if (file.newPath() != null && !file.binary()) {
                byFile.put(normalise(file.newPath()), file.addedLines());
            }
        }
        return new ChangedLines(byFile);
    }

    public Set<String> files() {
        return Collections.unmodifiableSet(byFile.keySet());
    }

    public SortedSet<Integer> lines(String file) {
        return byFile.getOrDefault(normalise(file), new TreeSet<>());
    }

    /**
     * True when any changed line of {@code file} falls within {@code tolerance} lines of {@code start..end}
     * (spec V1 uses ±3, because tools and reviewers point at a statement, not always at the edited line).
     */
    public boolean touches(String file, int start, int end, int tolerance) {
        SortedSet<Integer> lines = lines(file);
        return !lines.subSet(start - tolerance, end + tolerance + 1).isEmpty();
    }

    private static String normalise(String path) {
        return path.replace('\\', '/');
    }
}
