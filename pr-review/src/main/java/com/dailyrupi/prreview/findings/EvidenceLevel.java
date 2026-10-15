package com.dailyrupi.prreview.findings;

/** How strongly a finding is supported (spec §10). Only the first two can make a finding CONFIRMED. */
public enum EvidenceLevel {

    /** A tool, test, contract or coverage result reports the same issue. */
    CORROBORATED,
    /** Cites an existing requirement and the verifier substantiated it with checked citations. */
    REQUIREMENT_MATCH,
    /** The verifier substantiated it with checked citations, but nothing independent agrees. */
    GROUNDED,
    UNVERIFIED,
    CONTRADICTED
}
