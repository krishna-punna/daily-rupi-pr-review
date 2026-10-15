package com.dailyrupi.prreview.source;

import java.io.IOException;

import com.dailyrupi.prreview.diff.UnifiedDiffParser;

/** Treats two refs in a local repository as a pull request: {@code head} proposed for merge into {@code base}. */
public final class LocalGitPrSource {

    private final GitCli git;
    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    public LocalGitPrSource(GitCli git) {
        this.git = git;
    }

    public PullRequestSnapshot snapshot(String repository, String prId, String baseRef, String headRef)
            throws IOException {
        String baseSha = git.commitSha(baseRef);
        String headSha = git.commitSha(headRef);
        String mergeBase = git.mergeBase(baseSha, headSha);
        return new PullRequestSnapshot(repository, prId, headRef + " into " + baseRef, baseRef, baseSha, headRef,
                headSha, mergeBase, parser.parse(git.diff(mergeBase, headSha)));
    }

    /** The commit {@code headRef} points at now; compared with the analysed SHA to detect a stale analysis (R1). */
    public String currentHead(String headRef) throws IOException {
        return git.commitSha(headRef);
    }
}
