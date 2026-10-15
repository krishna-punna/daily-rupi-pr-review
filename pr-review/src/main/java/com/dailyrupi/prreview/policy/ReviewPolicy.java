package com.dailyrupi.prreview.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dailyrupi.prreview.findings.Severity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * pr-review/review-policy.yml (spec §11.1). The top-level fields are the merge-readiness policy; {@code project}
 * says where this repository keeps its code, contracts and checks. The file is versioned and every analysis
 * records {@link #policyVersion()}.
 */
public record ReviewPolicy(
        String policyVersion,
        List<String> reviewPaths,
        List<String> requiredChecks,
        Severity blockingSeverity,
        Severity investigationSeverity,
        double diffCoverageWarnBelow,
        int requiredApprovals,
        boolean requireAiReview,
        Enforcement enforcement,
        AiReviewTrigger aiReviewTrigger,
        ProjectConfig project) {

    public enum Enforcement {
        /** The check run concludes "neutral"; readiness is information only. */
        ADVISORY,
        /** The check run fails unless the PR is ready for approval or eligible to merge. */
        REQUIRED;

        @JsonCreator
        public static Enforcement parse(String value) {
            return valueOf(value.trim().toUpperCase());
        }

        @JsonValue
        public String yamlValue() {
            return name().toLowerCase();
        }
    }

    /** AI review costs money, so it only runs for these PRs; deterministic checks always run. */
    public record AiReviewTrigger(List<String> branchPrefixes, String label) {

        public AiReviewTrigger {
            branchPrefixes = branchPrefixes == null ? List.of() : List.copyOf(branchPrefixes);
        }
    }

    /**
     * @param moduleDir         directory holding the Maven project that is reviewed (backend)
     * @param sourceRoot        repository-relative main source root; tool reports are relative to it
     * @param contractTestClass test class whose cases are consumer-contract checks
     * @param contractsDir      where the consumer contract files live
     * @param noCoverageMinLines changed code with at least this many coverable lines and none covered is a finding
     * @param checks            how to run each check locally, in order, and which reports it leaves behind
     */
    public record ProjectConfig(
            String moduleDir,
            String sourceRoot,
            String contractTestClass,
            String contractsDir,
            int noCoverageMinLines,
            Map<String, CheckDefinition> checks) {

        public ProjectConfig {
            checks = checks == null ? Map.of() : new LinkedHashMap<>(checks);
        }
    }

    /**
     * @param mavenArgs Maven arguments that run the check, exactly as CI runs it (-B is added)
     * @param reports   report kind (surefire, jacoco, spotbugs) to path under the module
     */
    public record CheckDefinition(List<String> mavenArgs, Map<String, String> reports) {

        public CheckDefinition {
            mavenArgs = List.copyOf(mavenArgs);
            reports = reports == null ? Map.of() : new LinkedHashMap<>(reports);
        }
    }

    public ReviewPolicy {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("review-policy.yml must set policyVersion");
        }
        reviewPaths = reviewPaths == null ? List.of() : List.copyOf(reviewPaths);
        requiredChecks = requiredChecks == null ? List.of() : List.copyOf(requiredChecks);
        if (blockingSeverity == null || investigationSeverity == null || enforcement == null || project == null) {
            throw new IllegalArgumentException(
                    "review-policy.yml must set blockingSeverity, investigationSeverity, enforcement and project");
        }
    }
}
