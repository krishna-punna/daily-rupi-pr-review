package com.dailyrupi.prreview.readiness;

/** Whether the AI review ran for an analysis. AI findings are advisory, so anything but OK only adds uncertainty. */
public enum AiStatus {
    OK,
    UNAVAILABLE,
    /** Not run: no API key, outside the aiReviewTrigger, or not built yet. */
    SKIPPED,
    BUDGET_EXCEEDED
}
