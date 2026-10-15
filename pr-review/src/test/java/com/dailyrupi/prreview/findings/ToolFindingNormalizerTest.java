package com.dailyrupi.prreview.findings;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.dailyrupi.prreview.diff.ChangedLines;
import com.dailyrupi.prreview.diff.FileDiff;
import com.dailyrupi.prreview.diff.FileDiff.ChangeType;
import com.dailyrupi.prreview.diff.Hunk;
import com.dailyrupi.prreview.policy.ReviewPolicy.ProjectConfig;
import com.dailyrupi.prreview.tools.DiffCoverage;
import com.dailyrupi.prreview.tools.Fixtures;
import com.dailyrupi.prreview.tools.SastIssue;
import com.dailyrupi.prreview.tools.SpotBugsReportParser;
import com.dailyrupi.prreview.tools.SurefireReportParser;
import com.dailyrupi.prreview.tools.TestCaseResult;

import org.junit.jupiter.api.Test;

class ToolFindingNormalizerTest {

    private static final String SHA = "d03fff4";
    private static final String SCRATCH = "backend/src/main/java/com/dailyrupi/expense/ScratchSearch.java";

    private final ToolFindingNormalizer normalizer = new ToolFindingNormalizer(new ProjectConfig("backend",
            "backend/src/main/java", "com.dailyrupi.contract.ConsumerContractTest",
            "backend/src/test/resources/contracts/consumers", 3, Map.of()));

    private static ChangedLines changed(String path, Integer... lines) {
        return ChangedLines.of(List.of(new FileDiff(null, path, ChangeType.ADDED, false,
                List.of(new Hunk(0, 0, 1, lines.length, List.of(), new TreeSet<>(List.of(lines)))))));
    }

    private static List<SastIssue> sast() throws IOException {
        return new SpotBugsReportParser().parse(Fixtures.report("defective/sast/spotbugsXml.xml"));
    }

    @Test
    void injectionTheDiffIntroducesIsAConfirmedHighFindingCitingTheRequirement() throws IOException {
        List<Finding> findings = normalizer.fromSast(sast(), changed(SCRATCH, 17, 18, 19), SHA);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.category()).isEqualTo(Category.SECURITY_SQL_INJECTION);
            assertThat(f.severity()).isEqualTo(Severity.HIGH);
            assertThat(f.status()).isEqualTo(FindingStatus.CONFIRMED);
            assertThat(f.evidenceLevel()).isEqualTo(EvidenceLevel.CORROBORATED);
            assertThat(f.file()).isEqualTo(SCRATCH);
            assertThat(f.location()).isEqualTo(SCRATCH + ":19");
            assertThat(f.enclosingMethod()).isEqualTo("search(String)");
            assertThat(f.requirementIds()).containsExactly("REQ-SEC-001");
            assertThat(f.sources()).singleElement().satisfies(s -> {
                assertThat(s.ruleId()).isEqualTo("SQL_INJECTION_SPRING_JDBC");
                assertThat(s.reference()).isEqualTo("CWE-89");
                assertThat(s.independent()).isTrue();
            });
        });
    }

    @Test
    void toolFindingOutsideThePrsChangesIsPreExistingAndCannotBlock() throws IOException {
        List<Finding> farAway = normalizer.fromSast(sast(), changed(SCRATCH, 40), SHA);
        List<Finding> otherFile = normalizer.fromSast(sast(), changed("backend/src/main/java/Other.java", 19), SHA);

        assertThat(farAway).singleElement().extracting(Finding::status).isEqualTo(FindingStatus.PRE_EXISTING);
        assertThat(otherFile).singleElement().extracting(Finding::status).isEqualTo(FindingStatus.PRE_EXISTING);
    }

    @Test
    void eachBrokenConsumerContractBecomesItsOwnConfirmedCompatibilityFinding() throws IOException {
        List<TestCaseResult> contract = new SurefireReportParser()
                .parseDirectory(Fixtures.report("defective/contract-tests/surefire-reports")).orElseThrow();
        List<TestCaseResult> unit = new SurefireReportParser()
                .parseDirectory(Fixtures.report("defective/backend-tests/surefire-reports")).orElseThrow();

        List<Finding> findings = normalizer.fromContractTests(contract, SHA);

        assertThat(findings).extracting(Finding::title).containsExactlyInAnyOrder(
                "Consumer contract broken: android-sync: Changes and deletions since a time",
                "Consumer contract broken: web-frontend: Expense list",
                "Consumer contract broken: web-frontend: Create expense");
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.category()).isEqualTo(Category.API_COMPATIBILITY_BREAKING_CHANGE);
            assertThat(f.severity()).isEqualTo(Severity.HIGH);
            assertThat(f.status()).isEqualTo(FindingStatus.CONFIRMED);
            assertThat(f.requirementIds()).containsExactly("REQ-API-001");
            assertThat(f.rationale()).contains("paymentMethodName is missing").doesNotContain("\n");
        });
        assertThat(findings).extracting(Finding::file).contains(
                "backend/src/test/resources/contracts/consumers/web-frontend.json",
                "backend/src/test/resources/contracts/consumers/android-sync.json");
        assertThat(findings).extracting(Finding::fingerprint).doesNotHaveDuplicates();
        // The evidence names the exact field each consumer lost.
        assertThat(findings).flatExtracting(Finding::sources).extracting(FindingSource::reference)
                .containsExactlyInAnyOrder("expenses[].paymentMethodName is missing",
                        "content[].paymentMethodName is missing", "paymentMethodName is missing");
        // Ordinary failing tests are reported through their check, not as contract findings.
        assertThat(normalizer.fromContractTests(unit, SHA)).isEmpty();
    }

    @Test
    void changedCodeWithNoCoverageAtAllIsAFindingButPartlyCoveredCodeIsNot() {
        DiffCoverage coverage = new DiffCoverage(10, 2, List.of(
                new DiffCoverage.FileCoverage("backend/src/main/java/A.java", 4, 0, new TreeSet<>(List.of(5, 6, 7, 9))),
                new DiffCoverage.FileCoverage("backend/src/main/java/B.java", 4, 2, new TreeSet<>(List.of(3, 4))),
                new DiffCoverage.FileCoverage("backend/src/main/java/C.java", 2, 0, new TreeSet<>(List.of(1, 2)))));

        List<Finding> findings = normalizer.fromCoverage(coverage, SHA);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.file()).isEqualTo("backend/src/main/java/A.java");
            assertThat(f.category()).isEqualTo(Category.TEST_MISSING_OR_INADEQUATE);
            assertThat(f.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(f.location()).isEqualTo("backend/src/main/java/A.java:5-9");
        });
    }

    @Test
    void numberingPutsBlockingFindingsFirstThenMostSevere() {
        Finding preExisting = Finding.builder().category(Category.SECURITY_OTHER).severity(Severity.CRITICAL)
                .title("old").status(FindingStatus.PRE_EXISTING).build();
        Finding medium = Finding.builder().category(Category.TEST_MISSING_OR_INADEQUATE).severity(Severity.MEDIUM)
                .title("tests").status(FindingStatus.CONFIRMED).build();
        Finding high = Finding.builder().category(Category.SECURITY_SQL_INJECTION).severity(Severity.HIGH)
                .title("sql").status(FindingStatus.CONFIRMED).build();

        List<Finding> numbered = ToolFindingNormalizer.numbered(List.of(preExisting, medium, high));

        assertThat(numbered).extracting(Finding::findingId, Finding::title).containsExactly(
                org.assertj.core.groups.Tuple.tuple("F-001", "sql"),
                org.assertj.core.groups.Tuple.tuple("F-002", "tests"),
                org.assertj.core.groups.Tuple.tuple("F-003", "old"));
    }

    @Test
    void contractCheckNameDropsTheTestMethodPrefix() {
        assertThat(ToolFindingNormalizer.contractCheckName("everyConsumerGetsTheFieldsItReads() web-frontend: Expense list"))
                .isEqualTo("web-frontend: Expense list");
        assertThat(ToolFindingNormalizer.contractCheckName("plain name")).isEqualTo("plain name");
    }
}
