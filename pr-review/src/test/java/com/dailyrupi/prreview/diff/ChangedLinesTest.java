package com.dailyrupi.prreview.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;

import com.dailyrupi.prreview.diff.FileDiff.ChangeType;

import org.junit.jupiter.api.Test;

class ChangedLinesTest {

    private final ChangedLines changed = ChangedLines.of(List.of(new FileDiff("src/A.java", "src/A.java",
            ChangeType.MODIFIED, false, List.of(new Hunk(10, 1, 10, 1, List.of("+x"), new TreeSet<>(List.of(10)))))));

    @Test
    void lineRangeWithinToleranceOfAChangeTouchesIt() {
        assertThat(changed.touches("src/A.java", 13, 13, 3)).isTrue();
        assertThat(changed.touches("src/A.java", 5, 7, 3)).isTrue();
        assertThat(changed.touches("src/A.java", 14, 20, 3)).isFalse();
        assertThat(changed.touches("src/A.java", 1, 6, 3)).isFalse();
    }

    @Test
    void unchangedFileAndWindowsSeparatorsAreHandled() {
        assertThat(changed.touches("src/B.java", 10, 10, 3)).isFalse();
        assertThat(changed.touches("src\\A.java", 10, 10, 0)).isTrue();
    }
}
