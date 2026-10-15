package com.dailyrupi.prreview.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dailyrupi.prreview.diff.FileDiff.ChangeType;

/**
 * Parses {@code git diff} output. Hunk bodies are read by their line counts, not by their prefixes, so a
 * removed line that happens to start with "--" or "++" is never mistaken for a file header.
 */
public final class UnifiedDiffParser {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");

    public List<FileDiff> parse(String diff) {
        List<FileDiff> files = new ArrayList<>();
        FileState file = null;
        HunkState hunk = null;

        for (String raw : diff.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;

            if (hunk != null) {
                if (line.startsWith("\\")) {
                    continue; // "\ No newline at end of file"
                }
                if (hunk.consume(line)) {
                    if (hunk.done()) {
                        file.hunks.add(hunk.build());
                        hunk = null;
                    }
                    continue;
                }
                // A short hunk (truncated output): keep what was read and fall through to header handling.
                file.hunks.add(hunk.build());
                hunk = null;
            }

            if (line.startsWith("diff --git ")) {
                if (file != null) {
                    files.add(file.build());
                }
                file = new FileState(line.substring("diff --git ".length()));
                continue;
            }
            if (file == null) {
                continue;
            }
            Matcher header = HUNK_HEADER.matcher(line);
            if (header.matches()) {
                hunk = new HunkState(Integer.parseInt(header.group(1)), count(header.group(2)),
                        Integer.parseInt(header.group(3)), count(header.group(4)));
                if (hunk.done()) { // "@@ -0,0 +0,0 @@" style empty hunk
                    file.hunks.add(hunk.build());
                    hunk = null;
                }
            } else if (line.startsWith("new file mode")) {
                file.type = ChangeType.ADDED;
            } else if (line.startsWith("deleted file mode")) {
                file.type = ChangeType.DELETED;
            } else if (line.startsWith("rename from ")) {
                file.type = ChangeType.RENAMED;
                file.oldPath = unquote(line.substring("rename from ".length()));
            } else if (line.startsWith("rename to ")) {
                file.type = ChangeType.RENAMED;
                file.newPath = unquote(line.substring("rename to ".length()));
            } else if (line.startsWith("--- ")) {
                file.oldPath = stripPrefix(line.substring(4), "a/");
            } else if (line.startsWith("+++ ")) {
                file.newPath = stripPrefix(line.substring(4), "b/");
            } else if (line.startsWith("Binary files ")) {
                file.binary = true;
            }
        }
        if (hunk != null) {
            file.hunks.add(hunk.build());
        }
        if (file != null) {
            files.add(file.build());
        }
        return files;
    }

    private static int count(String group) {
        return group == null ? 1 : Integer.parseInt(group);
    }

    /** "a/x" becomes "x"; "/dev/null" becomes null; quoted paths are unquoted. */
    private static String stripPrefix(String path, String prefix) {
        String unquoted = unquote(path.split("\t", 2)[0]);
        if ("/dev/null".equals(unquoted)) {
            return null;
        }
        return unquoted.startsWith(prefix) ? unquoted.substring(prefix.length()) : unquoted;
    }

    private static String unquote(String path) {
        if (path.length() < 2 || !path.startsWith("\"") || !path.endsWith("\"")) {
            return path;
        }
        StringBuilder out = new StringBuilder();
        String body = path.substring(1, path.length() - 1);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(++i);
                out.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final class FileState {

        private String oldPath;
        private String newPath;
        private ChangeType type = ChangeType.MODIFIED;
        private boolean binary;
        private final List<Hunk> hunks = new ArrayList<>();

        FileState(String gitHeaderPaths) {
            // Fallback for diffs with no ---/+++ lines (binary or mode-only): "a/x b/x".
            String paths = gitHeaderPaths.trim();
            int split = paths.indexOf(" b/");
            if (paths.startsWith("a/") && split > 0) {
                oldPath = paths.substring(2, split);
                newPath = paths.substring(split + 3);
            }
        }

        FileDiff build() {
            if (type == ChangeType.ADDED) {
                oldPath = null;
            } else if (type == ChangeType.DELETED) {
                newPath = null;
            }
            return new FileDiff(oldPath, newPath, type, binary, hunks);
        }
    }

    private static final class HunkState {

        private final int oldStart;
        private final int oldCount;
        private final int newStart;
        private final int newCount;
        private int oldSeen;
        private int newSeen;
        private final List<String> lines = new ArrayList<>();
        private final SortedSet<Integer> added = new TreeSet<>();

        HunkState(int oldStart, int oldCount, int newStart, int newCount) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
        }

        /** Takes one body line; false if the line cannot belong to this hunk. */
        boolean consume(String line) {
            char kind = line.isEmpty() ? ' ' : line.charAt(0); // some tools drop the space of empty context lines
            switch (kind) {
                case ' ' -> {
                    oldSeen++;
                    newSeen++;
                }
                case '-' -> oldSeen++;
                case '+' -> {
                    added.add(newStart + newSeen);
                    newSeen++;
                }
                default -> {
                    return false;
                }
            }
            lines.add(line);
            return true;
        }

        boolean done() {
            return oldSeen >= oldCount && newSeen >= newCount;
        }

        Hunk build() {
            return new Hunk(oldStart, oldCount, newStart, newCount, lines, added);
        }
    }
}
