package com.dailyrupi.prreview.readiness;

/** Merge-readiness outcome (spec §10 of the solution design, §11 of the PoC spec). Most severe first. */
public enum ReadinessStatus {

    /** At least one required condition is not met. */
    NOT_READY,
    /** Evidence is insufficient or conflicting for a material issue. */
    NEEDS_INVESTIGATION,
    /** Automated requirements are met; a required human approval is still missing. */
    READY_FOR_HUMAN_APPROVAL,
    /** Every policy condition is met at the time of evaluation. Merging is still a human action. */
    ELIGIBLE_FOR_MERGE;

    public boolean moreSevereThan(ReadinessStatus other) {
        return ordinal() < other.ordinal();
    }
}
