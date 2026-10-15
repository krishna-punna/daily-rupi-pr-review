package com.dailyrupi.prreview.findings;

/**
 * Where a finding came from. {@code tool} and {@code ruleId} name the analyser and its rule (for example
 * findsecbugs / SQL_INJECTION_SPRING_JDBC); {@code reference} points at the evidence (a test name, a report).
 */
public record FindingSource(Type type, String tool, String ruleId, String reference) {

    public enum Type {
        AI_GENERATOR, AI_VERIFIER, SAST, TEST, CONTRACT, COVERAGE, HUMAN
    }

    /** True for sources that count as independent evidence (spec P2): anything but the AI itself or a human claim. */
    public boolean independent() {
        return type == Type.SAST || type == Type.TEST || type == Type.CONTRACT || type == Type.COVERAGE;
    }
}
