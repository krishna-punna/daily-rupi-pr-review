package com.dailyrupi.prreview.diff;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * One {@code @@ -oldStart,oldCount +newStart,newCount @@} block. {@code lines} keeps the raw body (with its
 * ' ', '+', '-' prefixes) for review context; {@code addedLines} are new-file line numbers of '+' lines.
 */
public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<String> lines,
        SortedSet<Integer> addedLines) {

    public Hunk {
        lines = List.copyOf(lines);
        addedLines = new TreeSet<>(addedLines);
    }
}
