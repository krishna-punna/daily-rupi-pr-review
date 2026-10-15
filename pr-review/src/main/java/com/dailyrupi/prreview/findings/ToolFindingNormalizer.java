package com.dailyrupi.prreview.findings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.dailyrupi.prreview.diff.ChangedLines;
import com.dailyrupi.prreview.findings.FindingSource.Type;
import com.dailyrupi.prreview.policy.ReviewPolicy.ProjectConfig;
import com.dailyrupi.prreview.tools.DiffCoverage;
import com.dailyrupi.prreview.tools.SastIssue;
import com.dailyrupi.prreview.tools.TestCaseResult;

/**
 * Turns deterministic tool results into canonical findings. These are independent evidence by definition
 * (spec P2), so a tool finding the PR introduces is CONFIRMED; one outside the PR's changes is PRE_EXISTING
 * and never blocks.
 */
public final class ToolFindingNormalizer {

    /** Spec V1: tools point at a statement, not always at the edited line. */
    public static final int LOCATION_TOLERANCE = 3;

    private static final String CONTRACT_REQUIREMENT = "REQ-API-001";

    private final ProjectConfig project;

    public ToolFindingNormalizer(ProjectConfig project) {
        this.project = project;
    }

    public List<Finding> fromSast(List<SastIssue> issues, ChangedLines changed, String headSha) {
        List<Finding> findings = new ArrayList<>();
        for (SastIssue issue : issues) {
            String file = issue.sourcePath() == null ? null : project.sourceRoot() + "/" + issue.sourcePath();
            boolean introduced = file != null && issue.startLine() != null
                    && changed.touches(file, issue.startLine(), lastLine(issue), LOCATION_TOLERANCE);
            Category category = SastRules.category(issue.type());
            Finding.Builder finding = Finding.builder()
                    .fingerprint(Fingerprint.of(category, file, issue.methodKey(), issue.type()))
                    .category(category)
                    .severity(SastRules.severity(issue.type(), issue.priority()))
                    .title(issue.shortMessage() == null ? issue.type() : issue.shortMessage())
                    .file(file)
                    .lines(issue.startLine(), lastLine(issue))
                    .enclosingMethod(issue.readableMethod())
                    .rationale(issue.longMessage())
                    .recommendation(SastRules.recommendation(issue.type()))
                    .source(new FindingSource(Type.SAST, "findsecbugs", issue.type(),
                            issue.cweId() == null ? null : "CWE-" + issue.cweId()))
                    .evidenceLevel(EvidenceLevel.CORROBORATED)
                    .status(introduced ? FindingStatus.CONFIRMED : FindingStatus.PRE_EXISTING)
                    .firstSeenSha(headSha)
                    .lastEvaluatedSha(headSha);
            String requirement = SastRules.requirementId(issue.type());
            if (requirement != null) {
                finding.requirementId(requirement);
            }
            findings.add(finding.build());
        }
        return findings;
    }

    /** A failed consumer-contract check means the API dropped or changed a field a consumer reads. */
    public List<Finding> fromContractTests(List<TestCaseResult> cases, String headSha) {
        List<Finding> findings = new ArrayList<>();
        for (TestCaseResult result : cases) {
            if (!result.failed() || !project.contractTestClass().equals(result.className())) {
                continue;
            }
            String check = contractCheckName(result.name());
            int colon = check.indexOf(':');
            String consumer = colon > 0 ? check.substring(0, colon).trim() : "consumer";
            String contractFile = project.contractsDir() + "/" + consumer + ".json";
            List<String> problems = contractProblems(result.message());
            Category category = Category.API_COMPATIBILITY_BREAKING_CHANGE;
            findings.add(Finding.builder()
                    .fingerprint(Fingerprint.of(category, contractFile, check, "CONSUMER_CONTRACT"))
                    .category(category)
                    .severity(Severity.HIGH)
                    .title("Consumer contract broken: " + check)
                    .file(contractFile)
                    .rationale(problems.isEmpty() ? oneLine(result.message())
                            : "The " + consumer + " consumer reads fields this response no longer provides as expected: "
                                    + String.join("; ", problems))
                    .recommendation("Keep the field names and types this consumer reads, or version the API."
                            + " Adding fields is fine.")
                    .requirementId(CONTRACT_REQUIREMENT)
                    .source(new FindingSource(Type.CONTRACT, "consumer contract", null,
                            problems.isEmpty() ? result.displayName() : String.join("; ", problems)))
                    .evidenceLevel(EvidenceLevel.CORROBORATED)
                    .status(FindingStatus.CONFIRMED)
                    .firstSeenSha(headSha)
                    .lastEvaluatedSha(headSha)
                    .build());
        }
        return findings;
    }

    /**
     * Changed code that no test executes at all. Partly covered code is left to the diff-coverage warning
     * (W2), so this only reports files where nothing changed is covered and enough changed to matter.
     */
    public List<Finding> fromCoverage(DiffCoverage coverage, String headSha) {
        List<Finding> findings = new ArrayList<>();
        for (DiffCoverage.FileCoverage file : coverage.files()) {
            if (file.covered() > 0 || file.coverable() < project.noCoverageMinLines()) {
                continue;
            }
            Category category = Category.TEST_MISSING_OR_INADEQUATE;
            findings.add(Finding.builder()
                    .fingerprint(Fingerprint.of(category, file.file(), "", "NO_COVERAGE"))
                    .category(category)
                    .severity(Severity.MEDIUM)
                    .title("Changed code has no test coverage (0 of " + file.coverable() + " changed lines)")
                    .file(file.file())
                    .lines(file.uncoveredLines().first(), file.uncoveredLines().last())
                    .rationale("No test executes any of the changed lines " + file.uncoveredLines() + ".")
                    .recommendation("Add a test that exercises the new or changed behaviour.")
                    .source(new FindingSource(Type.COVERAGE, "jacoco", "NO_COVERAGE", null))
                    .evidenceLevel(EvidenceLevel.CORROBORATED)
                    .status(FindingStatus.CONFIRMED)
                    .firstSeenSha(headSha)
                    .lastEvaluatedSha(headSha)
                    .build());
        }
        return findings;
    }

    /** Blocking statuses first, then by severity, file and line; numbered F-001, F-002, ... in that order. */
    public static List<Finding> numbered(List<Finding> findings) {
        Map<FindingStatus, Integer> statusRank = Map.of(
                FindingStatus.REGRESSED, 0, FindingStatus.UNRESOLVED, 1, FindingStatus.CONFIRMED, 2,
                FindingStatus.NEEDS_REVIEW, 3, FindingStatus.FIX_CANDIDATE, 4);
        List<Finding> sorted = findings.stream()
                .sorted(Comparator.<Finding>comparingInt(f -> statusRank.getOrDefault(f.status(), 9))
                        .thenComparing(Finding::severity)
                        .thenComparing(f -> f.file() == null ? "" : f.file())
                        .thenComparing(f -> f.lineStart() == null ? 0 : f.lineStart())
                        .thenComparing(Finding::title))
                .toList();
        List<Finding> numbered = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            numbered.add(sorted.get(i).toBuilder().findingId(String.format("F-%03d", i + 1)).build());
        }
        return numbered;
    }

    /** "everyConsumerGetsTheFieldsItReads() web-frontend: Expense list" becomes "web-frontend: Expense list". */
    static String contractCheckName(String testName) {
        int split = testName.indexOf(") ");
        return split >= 0 ? testName.substring(split + 2).trim() : testName.trim();
    }

    /**
     * The contract test reports its problems as an AssertJ list, for example
     * {@code Expecting empty but was: ["content[].paymentMethodName is missing"]}; pull out the quoted entries.
     */
    static List<String> contractProblems(String message) {
        List<String> problems = new ArrayList<>();
        if (message == null) {
            return problems;
        }
        int list = message.indexOf("but was:");
        if (list < 0) {
            return problems;
        }
        java.util.regex.Matcher quoted = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(message.substring(list));
        while (quoted.find()) {
            problems.add(quoted.group(1));
        }
        return problems;
    }

    private static Integer lastLine(SastIssue issue) {
        return issue.endLine() != null ? issue.endLine() : issue.startLine();
    }

    private static String oneLine(String message) {
        return message == null ? null : message.replaceAll("\\s+", " ").trim();
    }
}
