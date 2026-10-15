package com.dailyrupi.prreview.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

/**
 * Runs external commands (git, mvn). Output goes to files rather than pipes, so a chatty process can never
 * stall on a full pipe buffer, and output is decoded leniently because Windows tools do not always write UTF-8.
 */
public final class ProcessRunner {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {

        public boolean succeeded() {
            return exitCode == 0 && !timedOut;
        }
    }

    private ProcessRunner() {
    }

    /** Runs to completion and returns stdout and stderr separately. */
    public static Result capture(List<String> command, Path workingDir, Duration timeout) throws IOException {
        Path out = Files.createTempFile("pr-review-", ".out");
        Path err = Files.createTempFile("pr-review-", ".err");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(workingDir.toFile())
                    .redirectOutput(out.toFile()).redirectError(err.toFile());
            OptionalInt exit = run(builder, timeout, command);
            return new Result(exit.orElse(-1), read(out), read(err), exit.isEmpty());
        } finally {
            Files.deleteIfExists(out);
            Files.deleteIfExists(err);
        }
    }

    /** Runs to completion with stdout and stderr merged into {@code logFile}; the log is kept for later reading. */
    public static Result toLog(List<String> command, Path workingDir, Duration timeout, Path logFile) throws IOException {
        Files.createDirectories(logFile.toAbsolutePath().getParent());
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDir.toFile())
                .redirectErrorStream(true).redirectOutput(logFile.toFile());
        OptionalInt exit = run(builder, timeout, command);
        return new Result(exit.orElse(-1), "", "", exit.isEmpty());
    }

    /** The exit code, or empty when the process ran past {@code timeout} and was killed. */
    private static OptionalInt run(ProcessBuilder builder, Duration timeout, List<String> command) throws IOException {
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return OptionalInt.empty();
            }
            return OptionalInt.of(process.exitValue());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command.get(0), e);
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
