package com.dailyrupi.prreview.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.dailyrupi.prreview.tools.TestCaseResult.Outcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The three report parsers, against real reports from backend/. */
class ReportParsersTest {

    @Test
    void spotBugsReportYieldsTheInjectionWithItsMethodAndLine() throws IOException {
        List<SastIssue> issues = new SpotBugsReportParser().parse(Fixtures.report("defective/sast/spotbugsXml.xml"));

        assertThat(issues).hasSize(1);
        SastIssue issue = issues.get(0);
        assertThat(issue.type()).isEqualTo("SQL_INJECTION_SPRING_JDBC");
        assertThat(issue.category()).isEqualTo("SECURITY");
        assertThat(issue.priority()).isEqualTo(2);
        assertThat(issue.cweId()).isEqualTo(89);
        assertThat(issue.className()).isEqualTo("com.dailyrupi.expense.ScratchSearch");
        assertThat(issue.readableMethod()).isEqualTo("search(String)");
        assertThat(issue.methodKey()).isEqualTo("com.dailyrupi.expense.ScratchSearch.search(Ljava/lang/String;)Ljava/util/List;");
        assertThat(issue.sourcePath()).isEqualTo("com/dailyrupi/expense/ScratchSearch.java");
        assertThat(issue.startLine()).isEqualTo(19);
        assertThat(issue.endLine()).isEqualTo(19);
        assertThat(issue.shortMessage()).isEqualTo("Potential JDBC Injection (Spring JDBC)");
    }

    @Test
    void xmlParserNeverResolvesExternalEntities(@TempDir Path dir) throws IOException {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET");
        Path report = dir.resolve("spotbugsXml.xml");
        Files.writeString(report, "<?xml version=\"1.0\"?><!DOCTYPE b [<!ENTITY xxe SYSTEM \"" + secret.toUri()
                + "\">]><BugCollection><BugInstance type=\"X\" priority=\"2\"><ShortMessage>&xxe;</ShortMessage>"
                + "</BugInstance></BugCollection>");

        Throwable thrown = catchThrowable(() -> {
            List<SastIssue> issues = new SpotBugsReportParser().parse(report);
            assertThat(issues.get(0).shortMessage()).doesNotContain("TOP-SECRET");
        });

        // Refusing the document outright is fine too; leaking the file is not.
        if (thrown != null) {
            assertThat(thrown).isInstanceOf(IOException.class).hasMessageNotContaining("TOP-SECRET");
        }
    }

    @Test
    void surefireReportsListEveryCaseAndWhichOnesFailed() throws IOException {
        SurefireReportParser parser = new SurefireReportParser();

        List<TestCaseResult> clean = parser.parseDirectory(Fixtures.report("clean/backend-tests/surefire-reports")).orElseThrow();
        List<TestCaseResult> defective = parser.parseDirectory(Fixtures.report("defective/backend-tests/surefire-reports")).orElseThrow();

        assertThat(clean).hasSize(35).noneMatch(TestCaseResult::failed);
        assertThat(defective).hasSize(35);
        assertThat(defective).filteredOn(TestCaseResult::failed)
                .extracting(TestCaseResult::displayName)
                .containsExactly("ExpenseAndStatusTest.expenseCanBeAddedEditedListedAndDeleted");
        assertThat(defective).filteredOn(TestCaseResult::failed).first()
                .satisfies(t -> assertThat(t.message()).contains("$.paymentMethodName"));
    }

    @Test
    void contractReportNamesEachConsumerCheckAndItsMissingField() throws IOException {
        List<TestCaseResult> cases = new SurefireReportParser()
                .parseDirectory(Fixtures.report("defective/contract-tests/surefire-reports")).orElseThrow();

        assertThat(cases).hasSize(13);
        assertThat(cases).filteredOn(t -> t.outcome() == Outcome.FAILED)
                .extracting(TestCaseResult::name)
                .containsExactlyInAnyOrder(
                        "everyConsumerGetsTheFieldsItReads() android-sync: Changes and deletions since a time",
                        "everyConsumerGetsTheFieldsItReads() web-frontend: Expense list",
                        "everyConsumerGetsTheFieldsItReads() web-frontend: Create expense");
        assertThat(cases).filteredOn(TestCaseResult::failed)
                .allSatisfy(t -> assertThat(t.message()).contains("paymentMethodName is missing"));
    }

    @Test
    void missingOrEmptyReportDirectoryIsNotAPass(@TempDir Path dir) throws IOException {
        SurefireReportParser parser = new SurefireReportParser();

        assertThat(parser.parseDirectory(dir.resolve("absent"))).isEmpty();
        assertThat(parser.parseDirectory(dir)).isEmpty();
    }

    @Test
    void jacocoReportGivesPerLineCoverageByPathUnderTheSourceRoot() throws IOException {
        CoverageReport report = new JacocoReportParser().parse(Fixtures.report("clean/backend-tests/jacoco.xml"));

        assertThat(report.lines("com/dailyrupi/admin/AdminController.java"))
                .containsOnlyKeys(24, 25, 26, 31, 32, 33, 34)
                .allSatisfy((line, coverage) -> assertThat(coverage.covered()).isTrue());
        assertThat(report.lines("com/dailyrupi/common/GlobalExceptionHandler.java").get(38).covered()).isFalse();
        assertThat(report.lines("com/dailyrupi/no/Such.java")).isEmpty();
    }
}
