package com.dailyrupi.prreview.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.dailyrupi.prreview.diff.FileDiff.ChangeType;

import org.junit.jupiter.api.Test;

class UnifiedDiffParserTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    private static String diff(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    void modifiedFileReportsNewSideLineNumbersOfAddedLinesAcrossHunks() {
        List<FileDiff> files = parser.parse(diff(
                "diff --git a/src/A.java b/src/A.java",
                "index 1111111..2222222 100644",
                "--- a/src/A.java",
                "+++ b/src/A.java",
                "@@ -1,4 +1,5 @@",
                " line1",
                "-line2",
                "+line2 changed",
                "+line2b",
                " line3",
                " line4",
                "@@ -10,3 +11,3 @@ class A {",
                " ctx",
                "-old",
                "+new",
                " ctx"));

        assertThat(files).hasSize(1);
        FileDiff file = files.get(0);
        assertThat(file.path()).isEqualTo("src/A.java");
        assertThat(file.changeType()).isEqualTo(ChangeType.MODIFIED);
        assertThat(file.hunks()).hasSize(2);
        assertThat(file.addedLines()).containsExactly(2, 3, 12);
    }

    @Test
    void addedFileHasNoOldPathAndEveryLineIsAdded() {
        FileDiff file = parser.parse(diff(
                "diff --git a/docs/new.md b/docs/new.md",
                "new file mode 100644",
                "index 0000000..3333333",
                "--- /dev/null",
                "+++ b/docs/new.md",
                "@@ -0,0 +1,3 @@",
                "+a",
                "+b",
                "+c")).get(0);

        assertThat(file.changeType()).isEqualTo(ChangeType.ADDED);
        assertThat(file.oldPath()).isNull();
        assertThat(file.newPath()).isEqualTo("docs/new.md");
        assertThat(file.addedLines()).containsExactly(1, 2, 3);
    }

    @Test
    void deletedFileKeepsItsOldPathAndAddsNothing() {
        List<FileDiff> files = parser.parse(diff(
                "diff --git a/gone.txt b/gone.txt",
                "deleted file mode 100644",
                "index 4444444..0000000",
                "--- a/gone.txt",
                "+++ /dev/null",
                "@@ -1,2 +0,0 @@",
                "-a",
                "-b"));

        FileDiff file = files.get(0);
        assertThat(file.changeType()).isEqualTo(ChangeType.DELETED);
        assertThat(file.newPath()).isNull();
        assertThat(file.path()).isEqualTo("gone.txt");
        assertThat(file.addedLines()).isEmpty();
        assertThat(ChangedLines.of(files).files()).isEmpty();
    }

    @Test
    void renameRecordsBothPaths() {
        FileDiff file = parser.parse(diff(
                "diff --git a/old/A.java b/new/A.java",
                "similarity index 90%",
                "rename from old/A.java",
                "rename to new/A.java",
                "index 5555555..6666666 100644",
                "--- a/old/A.java",
                "+++ b/new/A.java",
                "@@ -2,2 +2,2 @@",
                " keep",
                "-x",
                "+y")).get(0);

        assertThat(file.changeType()).isEqualTo(ChangeType.RENAMED);
        assertThat(file.oldPath()).isEqualTo("old/A.java");
        assertThat(file.newPath()).isEqualTo("new/A.java");
        assertThat(file.addedLines()).containsExactly(3);
    }

    @Test
    void removedLineThatLooksLikeAHeaderStaysInsideItsHunk() {
        List<FileDiff> files = parser.parse(diff(
                "diff --git a/notes.md b/notes.md",
                "--- a/notes.md",
                "+++ b/notes.md",
                "@@ -1,2 +1,2 @@",
                "--- not a header, a removed markdown rule",
                "+++ not a header, an added line",
                " end",
                "diff --git a/second.md b/second.md",
                "--- a/second.md",
                "+++ b/second.md",
                "@@ -1 +1 @@",
                "-a",
                "+b"));

        assertThat(files).extracting(FileDiff::path).containsExactly("notes.md", "second.md");
        assertThat(files.get(0).newPath()).isEqualTo("notes.md");
        assertThat(files.get(0).addedLines()).containsExactly(1);
        assertThat(files.get(1).addedLines()).containsExactly(1);
    }

    @Test
    void crlfLineEndingsAndNoNewlineMarkersAreTolerated() {
        String windows = "diff --git a/w.txt b/w.txt\r\n--- a/w.txt\r\n+++ b/w.txt\r\n@@ -1 +1,2 @@\r\n a\r\n+b\r\n"
                + "\\ No newline at end of file\r\n";

        FileDiff file = parser.parse(windows).get(0);

        assertThat(file.path()).isEqualTo("w.txt");
        assertThat(file.addedLines()).containsExactly(2);
    }

    @Test
    void binaryFileIsMarkedAndContributesNoLines() {
        List<FileDiff> files = parser.parse(diff(
                "diff --git a/logo.png b/logo.png",
                "index 7777777..8888888 100644",
                "Binary files a/logo.png and b/logo.png differ"));

        assertThat(files.get(0).binary()).isTrue();
        assertThat(files.get(0).path()).isEqualTo("logo.png");
        assertThat(ChangedLines.of(files).files()).isEmpty();
    }

    @Test
    void emptyDiffHasNoFiles() {
        assertThat(parser.parse("")).isEmpty();
    }
}
