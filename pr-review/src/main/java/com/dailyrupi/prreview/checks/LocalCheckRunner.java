package com.dailyrupi.prreview.checks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.dailyrupi.prreview.checks.CheckResult.Status;
import com.dailyrupi.prreview.policy.ReviewPolicy.CheckDefinition;
import com.dailyrupi.prreview.policy.ReviewPolicy.ProjectConfig;
import com.dailyrupi.prreview.support.ProcessRunner;

/**
 * Runs each check with Maven, the way .github/workflows/pr-review.yml runs it, inside a worktree of the commit
 * under review. The exit code is the authoritative result; the reports are copied out for the analysis.
 */
public final class LocalCheckRunner {

    private final String maven;
    private final Duration timeout;
    private final Clock clock;

    public LocalCheckRunner(String maven, Duration timeout, Clock clock) {
        this.maven = maven;
        this.timeout = timeout;
        this.clock = clock;
    }

    /** "mvn.cmd" on Windows, where ProcessBuilder does not resolve batch files by their bare name. */
    public static String defaultMaven() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows") ? "mvn.cmd" : "mvn";
    }

    public Map<String, CheckResult> run(Path worktree, ProjectConfig project, Path reportsRoot, Path logsDir,
            Consumer<String> progress) throws IOException {
        Path module = worktree.resolve(project.moduleDir()).normalize();
        Map<String, CheckResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, CheckDefinition> entry : project.checks().entrySet()) {
            String name = entry.getKey();
            CheckDefinition definition = entry.getValue();
            List<String> command = new ArrayList<>(List.of(maven, "-B"));
            command.addAll(definition.mavenArgs());
            progress.accept("Running " + name + ": mvn -B " + String.join(" ", definition.mavenArgs()));

            // Reports from the previous check would otherwise be read as this check's.
            for (String report : definition.reports().values()) {
                deleteInside(module, module.resolve(report));
            }
            Path log = logsDir.resolve(name + ".log");
            long started = System.nanoTime();
            ProcessRunner.Result run = ProcessRunner.toLog(command, module, timeout, log);
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            Path destination = reportsRoot.resolve(name);
            for (String report : definition.reports().values()) {
                copyIfPresent(module.resolve(report), destination);
            }
            Status status = run.timedOut() ? Status.ERROR : run.succeeded() ? Status.PASSED : Status.FAILED;
            String detail = run.timedOut() ? "timed out after " + timeout.toMinutes() + " minutes"
                    : CheckDetails.describe(new CheckReports(destination));
            if (status == Status.FAILED && detail == null) {
                detail = "exit code " + run.exitCode() + ", see " + log.getFileName();
            }
            progress.accept("  " + name + ": " + status + " in " + took.toSeconds() + " s");
            results.put(name, new CheckResult(name, status, "local run: mvn -B " + String.join(" ", definition.mavenArgs()),
                    detail, clock.instant()));
        }
        return results;
    }

    private static void copyIfPresent(Path source, Path destination) throws IOException {
        if (Files.isDirectory(source)) {
            Path target = destination.resolve(source.getFileName());
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path path : (Iterable<Path>) walk::iterator) {
                    Path copy = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } else if (Files.isRegularFile(source)) {
            Files.createDirectories(destination);
            Files.copy(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes {@code target} (a file or a folder), refusing anything outside {@code root}. */
    private static void deleteInside(Path root, Path target) throws IOException {
        Path normalised = target.normalize();
        if (!normalised.startsWith(root) || normalised.equals(root)) {
            throw new IOException("Refusing to delete " + target + ": not inside " + root);
        }
        if (!Files.exists(normalised)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(normalised)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
