package com.dailyrupi.prreview.findings;

/** Finding lifecycle (spec §9.1). */
public enum FindingStatus {

    DETECTED,
    VALIDATING,
    /** Backed by independent evidence (a tool, a test, a contract, coverage, or an explicit requirement). */
    CONFIRMED,
    /** AI-only or insufficient context. Never blocks. */
    NEEDS_REVIEW,
    REJECTED,
    SUPPRESSED_DUPLICATE,
    /** Already present before this PR; reported, never blocks. */
    PRE_EXISTING,
    FIX_CANDIDATE,
    VERIFIED_FIXED,
    UNRESOLVED,
    REGRESSED,
    /** The code was removed without a verifiable fix. */
    OBSOLETE
}
