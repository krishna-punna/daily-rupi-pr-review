package com.dailyrupi.prreview.source;

import java.util.List;

import com.dailyrupi.prreview.diff.ChangedLines;
import com.dailyrupi.prreview.diff.FileDiff;

/**
 * Provider-neutral view of one pull request at one head commit. {@code diffBaseSha} is the merge base, so the
 * diff shows only what the PR's own commits change, as GitHub's "Files changed" tab does.
 */
public record PullRequestSnapshot(
        String repository,
        String prId,
        String title,
        String baseRef,
        String baseSha,
        String headRef,
        String headSha,
        String diffBaseSha,
        List<FileDiff> files) {

    public PullRequestSnapshot {
        files = List.copyOf(files);
    }

    public ChangedLines changedLines() {
        return ChangedLines.of(files);
    }

    public String shortHeadSha() {
        return headSha.substring(0, Math.min(7, headSha.length()));
    }
}
