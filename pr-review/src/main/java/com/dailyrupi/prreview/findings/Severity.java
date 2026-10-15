package com.dailyrupi.prreview.findings;

/** Most severe first, so declaration order is the ranking. */
public enum Severity {

    CRITICAL, HIGH, MEDIUM, LOW, INFO;

    /** True when this severity is the same as, or more severe than, {@code threshold}. */
    public boolean atLeast(Severity threshold) {
        return ordinal() <= threshold.ordinal();
    }
}
