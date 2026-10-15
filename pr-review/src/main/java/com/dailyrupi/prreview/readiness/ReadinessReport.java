package com.dailyrupi.prreview.readiness;

import java.time.Instant;
import java.util.List;

/**
 * The explainable outcome: the status, every rule that fired (R1–R8, W1–W5) and why, and the policy version
 * that produced it.
 */
public record ReadinessReport(
        ReadinessStatus status,
        List<String> blockingReasons,
        List<String> warnings,
        List<String> uncertainties,
        int requiredHumanApprovalsRemaining,
        List<String> rulesFired,
        String policyVersion,
        Instant evaluatedAt) {

    public ReadinessReport {
        blockingReasons = List.copyOf(blockingReasons);
        warnings = List.copyOf(warnings);
        uncertainties = List.copyOf(uncertainties);
        rulesFired = List.copyOf(rulesFired);
    }
}
