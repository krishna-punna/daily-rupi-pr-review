package com.dailyrupi.prreview.findings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprintTest {

    private static final String FILE = "backend/src/main/java/com/dailyrupi/expense/ScratchSearch.java";

    @Test
    void sameIssueGivesTheSameFingerprintEveryTime() {
        String first = Fingerprint.of(Category.SECURITY_SQL_INJECTION, FILE, "search(String)", "SQL_INJECTION_SPRING_JDBC");
        String second = Fingerprint.of(Category.SECURITY_SQL_INJECTION, FILE, "search(String)", "SQL_INJECTION_SPRING_JDBC");

        assertThat(first).isEqualTo(second).matches("sha1:[0-9a-f]{40}");
    }

    @Test
    void anyIdentifyingPartChangesTheFingerprint() {
        String base = Fingerprint.of(Category.SECURITY_SQL_INJECTION, FILE, "search(String)", "R");

        assertThat(Fingerprint.of(Category.SECURITY_OTHER, FILE, "search(String)", "R")).isNotEqualTo(base);
        assertThat(Fingerprint.of(Category.SECURITY_SQL_INJECTION, FILE + "x", "search(String)", "R")).isNotEqualTo(base);
        assertThat(Fingerprint.of(Category.SECURITY_SQL_INJECTION, FILE, "find(String)", "R")).isNotEqualTo(base);
        assertThat(Fingerprint.of(Category.SECURITY_SQL_INJECTION, FILE, "search(String)", "S")).isNotEqualTo(base);
    }

    @Test
    void windowsPathSeparatorsDoNotChangeIt() {
        assertThat(Fingerprint.of(Category.SECURITY_OTHER, FILE.replace('/', '\\'), "m", "R"))
                .isEqualTo(Fingerprint.of(Category.SECURITY_OTHER, FILE, "m", "R"));
    }
}
