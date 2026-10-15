package com.dailyrupi.prreview.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.dailyrupi.prreview.source.GitCli;
import com.dailyrupi.prreview.support.Json;
import com.dailyrupi.prreview.tools.Fixtures;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * The command end to end against a throwaway git repository, using the real reports instead of running Maven
 * (the full local run is exercised by the Phase 2 DoD on the real repository).
 */
class ReviewLocalCommandTest {

    @TempDir
    Path repo;

    @TempDir
    Path out;

    @Test
    void reviewWritesReportStateAndAuditAndReportsNotReady() throws IOException {
        GitCli git = new GitCli(repo);
        git.run("-c", "init.defaultBranch=main", "init", "--quiet");
        Files.writeString(repo.resolve("README.md"), "base\n");
        commit(git, "base");
        git.run("checkout", "--quiet", "-b", "feature/search");
        Path scratch = repo.resolve("backend/src/main/java/com/dailyrupi/expense/ScratchSearch.java");
        Files.createDirectories(scratch.getParent());
        Files.writeString(scratch, IntStream.rangeClosed(1, 21).mapToObj(i -> "// line " + i).collect(Collectors.joining("\n")));
        commit(git, "feature");

        StringWriter console = new StringWriter();
        CommandLine cli = new CommandLine(new PrReviewCommand());
        cli.setOut(new PrintWriter(console));
        int exit = cli.execute("review", "local", "--repo", repo.toString(), "--head", "feature/search",
                "--policy", Path.of("review-policy.yml").toAbsolutePath().toString(),
                "--reports-dir", Fixtures.report("defective").toString(), "--out", out.toString());

        assertThat(exit).as(console.toString()).isZero();
        assertThat(console.toString()).contains("Readiness: NOT_READY").contains("BLOCKING  Required check `sast` failed");

        Path folder = out.resolve("reports/local-feature_search").resolve(git.commitSha("feature/search").substring(0, 7));
        JsonNode report = Json.pretty().readTree(folder.resolve("review-report.json").toFile());
        assertThat(report.at("/readiness/status").asText()).isEqualTo("NOT_READY");
        assertThat(report.at("/findings/0/category").asText()).isEqualTo("SECURITY/SQL_INJECTION");
        assertThat(report.at("/findings/0/status").asText()).isEqualTo("CONFIRMED");
        assertThat(Files.readString(folder.resolve("review-report.md"))).startsWith("## PR review:");
        assertThat(out.resolve("state/pr-local-feature_search.json")).exists();
        assertThat(Files.readAllLines(out.resolve("audit/pr-local-feature_search.jsonl")))
                .first().asString().contains("ANALYSIS_STARTED");
        // The audit log records identifiers and statuses, never the reviewed code.
        assertThat(Files.readString(out.resolve("audit/pr-local-feature_search.jsonl"))).doesNotContain("// line");
    }

    private static void commit(GitCli git, String message) throws IOException {
        git.run("add", "-A");
        git.run("-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false",
                "commit", "--quiet", "-m", message);
    }
}
