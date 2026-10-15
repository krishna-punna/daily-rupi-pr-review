package com.dailyrupi.prreview.source;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.dailyrupi.prreview.support.ProcessRunner;

/** Thin wrapper over the {@code git} command line, run in one repository. Read-only apart from worktrees. */
public final class GitCli {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final Path repoDir;

    public GitCli(Path repoDir) {
        this.repoDir = repoDir;
    }

    public Path repoDir() {
        return repoDir;
    }

    /** Full 40-character SHA of {@code ref}; fails if it is not a commit. */
    public String commitSha(String ref) throws IOException {
        return run("rev-parse", "--verify", "--quiet", ref + "^{commit}").trim();
    }

    public String mergeBase(String a, String b) throws IOException {
        return run("merge-base", a, b).trim();
    }

    /** What {@code head} changes relative to {@code base}, as a unified diff with renames detected. */
    public String diff(String base, String head) throws IOException {
        return run("-c", "core.quotepath=off", "diff", "--no-color", "--no-ext-diff", "--unified=3",
                "--find-renames", base, head);
    }

    /** Checks {@code sha} out into {@code dir} as a detached worktree, so the user's own checkout is untouched. */
    public void addWorktree(Path dir, String sha) throws IOException {
        run("worktree", "add", "--detach", "--force", dir.toAbsolutePath().toString(), sha);
    }

    public void removeWorktree(Path dir) throws IOException {
        run("worktree", "remove", "--force", dir.toAbsolutePath().toString());
    }

    public String run(String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessRunner.Result result = ProcessRunner.capture(command, repoDir, TIMEOUT);
        if (!result.succeeded()) {
            throw new IOException("git " + String.join(" ", args) + " failed"
                    + (result.timedOut() ? " (timed out)" : " with exit code " + result.exitCode()) + ": "
                    + result.stderr().trim());
        }
        return result.stdout();
    }
}
