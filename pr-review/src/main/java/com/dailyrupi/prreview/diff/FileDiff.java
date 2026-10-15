package com.dailyrupi.prreview.diff;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** One file in a unified diff. {@code oldPath} is null for an added file, {@code newPath} for a deleted one. */
public record FileDiff(String oldPath, String newPath, ChangeType changeType, boolean binary, List<Hunk> hunks) {

    public enum ChangeType {
        ADDED, MODIFIED, DELETED, RENAMED
    }

    public FileDiff {
        hunks = List.copyOf(hunks);
    }

    /** The path the file has after the change (before it, for a deleted file). */
    public String path() {
        return newPath != null ? newPath : oldPath;
    }

    /** Line numbers in the new version that this diff adds or changes. */
    public SortedSet<Integer> addedLines() {
        SortedSet<Integer> lines = new TreeSet<>();
        hunks.forEach(hunk -> lines.addAll(hunk.addedLines()));
        return lines;
    }
}
