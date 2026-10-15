package com.dailyrupi.prreview.readiness;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.FindingSource;
import com.dailyrupi.prreview.findings.FindingStatus;
import com.dailyrupi.prreview.policy.ReviewPolicy;

/**
 * Merge readiness as a pure function of evidence and policy (spec §11). Every rule is evaluated; the most
 * severe status among those that fire wins. The engine never merges, never approves and never treats a
 * missing result as a pass.
 */
public final class ReadinessEngine {

    /** Finding statuses that can block: confirmed by independent evidence and not (yet) fixed. */
    private static final Set<FindingStatus> BLOCKING = EnumSet.of(
            FindingStatus.CONFIRMED, FindingStatus.UNRESOLVED, FindingStatus.REGRESSED);

    private final Clock clock;

    public ReadinessEngine(Clock clock) {
        this.clock = clock;
    }

    public ReadinessReport evaluate(ReadinessInput input, ReviewPolicy policy) {
        Outcome out = new Outcome();

        // R1: the analysis must be about the commit the PR points at now.
        if (!input.analysisHeadSha().equals(input.currentHeadSha())) {
            out.block("R1", "Analysis is for " + shortSha(input.analysisHeadSha()) + " but the PR head is now "
                    + shortSha(input.currentHeadSha()) + " (stale analysis; run it again)");
        }

        // R2 / R3: every required check must have passed. Missing, errored or running is not a pass.
        for (String name : policy.requiredChecks()) {
            CheckResult check = input.checks().get(name);
            if (check == null || check.status() == CheckResult.Status.MISSING) {
                out.block("R3", "Required check `" + name + "` is missing (a missing check never counts as passed)");
                continue;
            }
            switch (check.status()) {
                case FAILED -> out.block("R2", "Required check `" + name + "` failed" + detail(check));
                case ERROR -> out.block("R3", "Required check `" + name + "` could not be run" + detail(check));
                case RUNNING -> out.block("R3", "Required check `" + name + "` has not finished");
                default -> {
                    // PASSED
                }
            }
        }

        // R4: an unresolved merge conflict.
        if (Boolean.TRUE.equals(input.mergeConflict())) {
            out.block("R4", "The branch has a merge conflict with its base");
        }

        for (Finding finding : input.findings()) {
            boolean blockingSeverity = finding.severity().atLeast(policy.blockingSeverity());
            boolean investigationSeverity = finding.severity().atLeast(policy.investigationSeverity());
            if (BLOCKING.contains(finding.status())) {
                if (blockingSeverity) {
                    out.block("R5", describe(finding)); // R5: confirmed, unresolved or regressed, and severe enough
                } else {
                    out.warn("W5", describe(finding)); // W5: confirmed but below the blocking severity
                }
            } else if (finding.status() == FindingStatus.NEEDS_REVIEW) {
                // AI-only findings never block (spec P2); a severe one needs a human to look.
                if (investigationSeverity) {
                    out.investigate("R6", describe(finding) + "; needs human confirmation");
                } else {
                    out.warn("W1", describe(finding) + "; needs human review");
                }
            } else if (finding.status() == FindingStatus.FIX_CANDIDATE) {
                out.warn("W3", describe(finding) + "; fix not verified yet");
            }
            // PRE_EXISTING, REJECTED, SUPPRESSED_DUPLICATE, VERIFIED_FIXED, OBSOLETE, DETECTED, VALIDATING:
            // reported elsewhere, never part of the decision.
        }

        // R6: AI review required by policy but did not run.
        if (policy.requireAiReview() && input.aiStatus() != AiStatus.OK) {
            out.investigate("R6", "The policy requires an AI review, but it did not run (" + input.aiStatus() + ")");
        } else if (input.aiStatus() != AiStatus.OK) {
            out.uncertainties.add("AI review did not run (" + input.aiStatus()
                    + "); this assessment rests on deterministic checks only");
        }

        // R7: human approval. Unknown counts as not given.
        int given = input.approvalsGiven() == null ? 0 : input.approvalsGiven();
        int remaining = Math.max(0, policy.requiredApprovals() - given);
        if (remaining > 0) {
            out.fire("R7", ReadinessStatus.READY_FOR_HUMAN_APPROVAL);
            if (input.approvalsGiven() == null) {
                out.uncertainties.add("Approvals are unknown here (local run), so they count as not given");
            }
        }

        // W2: tests cover too little of the changed code.
        if (input.diffCoverage() != null && input.diffCoverage() < policy.diffCoverageWarnBelow()) {
            out.warn("W2", String.format("Tests cover %.0f%% of the changed code, below the %.0f%% the policy expects",
                    input.diffCoverage() * 100, policy.diffCoverageWarnBelow() * 100));
        }

        // W4: impacted tests that did not run.
        if (!input.recommendedTestsNotRun().isEmpty()) {
            out.warn("W4", "Recommended tests did not run: " + String.join(", ", input.recommendedTestsNotRun()));
        }

        // R8: nothing above stood in the way.
        if (out.status == null) {
            out.fire("R8", ReadinessStatus.ELIGIBLE_FOR_MERGE);
        }

        out.uncertainties.addAll(input.notes());
        return new ReadinessReport(out.status, out.blocking, out.warnings, List.copyOf(new LinkedHashSet<>(out.uncertainties)),
                remaining, sortedRules(out.rules), policy.policyVersion(), clock.instant());
    }

    private static String describe(Finding finding) {
        String evidence = finding.sources().stream().filter(FindingSource::independent)
                .map(s -> (s.tool() == null ? s.type().name() : s.tool())
                        + (s.ruleId() == null ? "" : " " + s.ruleId())
                        + (s.reference() == null ? "" : " (" + s.reference() + ")"))
                .collect(Collectors.joining(", "));
        return (finding.findingId() == null ? "" : finding.findingId() + " ")
                + finding.category() + " " + finding.severity() + " is " + finding.status() + ": "
                + finding.title() + " (" + finding.location() + ")"
                + (evidence.isEmpty() ? "" : " [evidence: " + evidence + "]");
    }

    private static String detail(CheckResult check) {
        return check.detail() == null || check.detail().isBlank() ? "" : ": " + check.detail();
    }

    private static String shortSha(String sha) {
        return sha == null ? "(unknown)" : sha.substring(0, Math.min(7, sha.length()));
    }

    /** R1..R8 first, then W1..W5, each in numeric order. */
    private static List<String> sortedRules(Set<String> rules) {
        return rules.stream().sorted((a, b) -> {
            int kind = Character.compare(a.charAt(0), b.charAt(0)); // 'R' sorts before 'W'
            return kind != 0 ? kind : Integer.compare(Integer.parseInt(a.substring(1)), Integer.parseInt(b.substring(1)));
        }).toList();
    }

    private static final class Outcome {

        private ReadinessStatus status;
        private final Set<String> rules = new LinkedHashSet<>();
        private final List<String> blocking = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> uncertainties = new ArrayList<>();

        void fire(String rule, ReadinessStatus result) {
            rules.add(rule);
            if (status == null || result.moreSevereThan(status)) {
                status = result;
            }
        }

        void block(String rule, String reason) {
            fire(rule, ReadinessStatus.NOT_READY);
            blocking.add(reason);
        }

        void investigate(String rule, String reason) {
            fire(rule, ReadinessStatus.NEEDS_INVESTIGATION);
            uncertainties.add(reason);
        }

        void warn(String rule, String reason) {
            rules.add(rule);
            warnings.add(reason);
        }
    }
}
