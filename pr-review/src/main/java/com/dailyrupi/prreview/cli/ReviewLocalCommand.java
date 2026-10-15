package com.dailyrupi.prreview.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import com.dailyrupi.prreview.analysis.ReviewAnalyzer;
import com.dailyrupi.prreview.audit.AuditLog;
import com.dailyrupi.prreview.audit.AuditLog.Event;
import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.checks.LocalCheckRunner;
import com.dailyrupi.prreview.checks.ReportBasedChecks;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.policy.PolicyLoader;
import com.dailyrupi.prreview.policy.ReviewPolicy;
import com.dailyrupi.prreview.publish.MarkdownRenderer;
import com.dailyrupi.prreview.publish.ReviewReport;
import com.dailyrupi.prreview.source.GitCli;
import com.dailyrupi.prreview.source.LocalGitPrSource;
import com.dailyrupi.prreview.source.PullRequestSnapshot;
import com.dailyrupi.prreview.state.FileStateStore;
import com.dailyrupi.prreview.support.Json;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Reviews {@code --head} as if it were a pull request into {@code --base}, entirely on this machine: the checks run
 * in a throwaway worktree of the head commit, so the user's own checkout is never touched.
 */
@Command(name = "local", mixinStandardHelpOptions = true,
        description = "Review a local branch as a pull request: run the checks, collect findings, decide readiness.")
public class ReviewLocalCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--repo", defaultValue = ".", description = "Repository to review (default: current directory).")
    Path repo;

    @Option(names = "--base", defaultValue = "main", description = "Branch the PR would merge into (default: main).")
    String base;

    @Option(names = "--head", required = true, description = "Branch or commit under review.")
    String head;

    @Option(names = "--pr-id", description = "Identifier for state and reports (default: local-<head>).")
    String prId;

    @Option(names = "--policy", description = "Policy file (default: <repo>/pr-review/review-policy.yml).")
    Path policyFile;

    @Option(names = "--reports-dir", description = "Use reports already in this folder (one sub-folder per check) "
            + "instead of running the checks.")
    Path reportsDir;

    @Option(names = "--check", description = "Override a check result, e.g. --check sast=PASSED. Repeatable.")
    Map<String, CheckResult.Status> checkOverrides = new LinkedHashMap<>();

    @Option(names = "--out", description = "Where reports, state and the audit log go (default: <repo>/.ai-review).")
    Path out;

    @Option(names = "--mvn", description = "Maven executable (default: mvn, or mvn.cmd on Windows).")
    String maven = LocalCheckRunner.defaultMaven();

    @Option(names = "--timeout-minutes", defaultValue = "20", description = "Per-check time limit (default: 20).")
    long timeoutMinutes;

    @Option(names = "--keep-worktree", description = "Keep the worktree the checks ran in, for debugging.")
    boolean keepWorktree;

    private final Clock clock = Clock.systemUTC();

    @Override
    public Integer call() throws IOException {
        PrintWriter console = spec.commandLine().getOut();
        Path repoDir = repo.toAbsolutePath().normalize();
        GitCli git = new GitCli(repoDir);
        ReviewPolicy policy = new PolicyLoader().load(policyFile != null ? policyFile
                : repoDir.resolve("pr-review/review-policy.yml"));
        Path outDir = (out != null ? out : repoDir.resolve(".ai-review")).toAbsolutePath().normalize();
        String id = prId != null ? prId : "local-" + head;

        LocalGitPrSource source = new LocalGitPrSource(git);
        PullRequestSnapshot pr = source.snapshot(repoDir.getFileName().toString(), id, base, head);
        String analysisId = "an-" + FileStateStore.safeName(id) + "-" + pr.shortHeadSha();
        Path reportFolder = outDir.resolve("reports").resolve(FileStateStore.safeName(id)).resolve(pr.shortHeadSha());
        AuditLog audit = new AuditLog(outDir.resolve("audit").resolve("pr-" + FileStateStore.safeName(id) + ".jsonl"), clock);
        Instant startedAt = clock.instant();

        console.printf("Reviewing %s (%s) into %s (%s): %d changed file(s)%n", head, pr.shortHeadSha(), base,
                pr.baseSha().substring(0, 7), pr.files().size());
        console.flush();
        audit.record(Event.ANALYSIS_STARTED, analysisId, Map.of("headSha", pr.headSha(), "baseSha", pr.baseSha(),
                "diffBaseSha", pr.diffBaseSha(), "policyVersion", policy.policyVersion(), "changedFiles", pr.files().size()));

        Path reportsRoot;
        Map<String, CheckResult> checks;
        if (reportsDir != null) {
            reportsRoot = reportsDir.toAbsolutePath().normalize();
            checks = new ReportBasedChecks(clock).infer(reportsRoot, policy.requiredChecks());
        } else {
            reportsRoot = reportFolder.resolve("checks");
            checks = runChecksInWorktree(git, pr, policy, outDir, reportsRoot, reportFolder.resolve("logs"), console);
        }
        checks = new LinkedHashMap<>(checks);
        for (Map.Entry<String, CheckResult.Status> override : checkOverrides.entrySet()) {
            checks.put(override.getKey(), new CheckResult(override.getKey(), override.getValue(),
                    "command-line override", "set with --check", clock.instant()));
        }
        for (CheckResult check : checks.values()) {
            audit.record(Event.CHECK_RESULT, analysisId, Map.of("check", check.name(), "status", check.status().name(),
                    "source", check.source()));
        }

        ReviewReport report = new ReviewAnalyzer(clock).analyze(analysisId, pr, policy, reportsRoot, checks,
                source.currentHead(head), null, startedAt);

        for (Finding finding : report.findings()) {
            audit.record(Event.FINDING_RECORDED, analysisId, Map.of("findingId", finding.findingId(),
                    "fingerprint", finding.fingerprint(), "category", finding.category().label(),
                    "severity", finding.severity().name(), "status", finding.status().name()));
        }
        audit.record(Event.READINESS_EVALUATED, analysisId, Map.of("status", report.readiness().status().name(),
                "rulesFired", report.readiness().rulesFired(), "policyVersion", report.policyVersion()));

        Files.createDirectories(reportFolder);
        Path json = reportFolder.resolve("review-report.json");
        Path markdown = reportFolder.resolve("review-report.md");
        Json.pretty().writeValue(json.toFile(), report);
        MarkdownRenderer renderer = new MarkdownRenderer();
        Files.writeString(markdown, renderer.render(report), StandardCharsets.UTF_8);
        Path state = new FileStateStore(outDir.resolve("state")).save(report);
        audit.record(Event.ANALYSIS_FINISHED, analysisId, Map.of("report", outDir.relativize(json).toString()));

        console.println();
        console.print(renderer.consoleSummary(report));
        console.println();
        console.println("Report: " + markdown);
        console.println("JSON:   " + json);
        console.println("State:  " + state);
        console.flush();
        return 0; // The analysis completed; readiness is reported, not signalled through the exit code.
    }

    private Map<String, CheckResult> runChecksInWorktree(GitCli git, PullRequestSnapshot pr, ReviewPolicy policy,
            Path outDir, Path reportsRoot, Path logs, PrintWriter console) throws IOException {
        Path worktree = outDir.resolve("work").resolve(pr.shortHeadSha());
        if (Files.exists(worktree)) {
            git.removeWorktree(worktree);
        }
        git.run("worktree", "prune");
        git.addWorktree(worktree, pr.headSha());
        try {
            return new LocalCheckRunner(maven, Duration.ofMinutes(timeoutMinutes), clock).run(worktree, policy.project(),
                    reportsRoot, logs, line -> {
                        console.println(line);
                        console.flush();
                    });
        } finally {
            if (!keepWorktree) {
                git.removeWorktree(worktree);
            }
        }
    }
}
