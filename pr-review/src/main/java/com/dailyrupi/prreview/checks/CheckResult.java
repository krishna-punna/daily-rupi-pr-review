package com.dailyrupi.prreview.checks;

import java.time.Instant;

/**
 * The authoritative result of one required check (spec §7.2 Quality Gate Adapter). {@code source} says where
 * the result came from (a local run, a CI artifact, the GitHub Checks API).
 */
public record CheckResult(String name, Status status, String source, String detail, Instant observedAt) {

    public enum Status {
        PASSED,
        FAILED,
        /** Never ran, or left no result. Not a pass (spec P3). */
        MISSING,
        /** Could not be run or read: a timeout, an unreadable report. Not a pass either. */
        ERROR,
        /** Still in progress. Not a pass yet. */
        RUNNING
    }
}
