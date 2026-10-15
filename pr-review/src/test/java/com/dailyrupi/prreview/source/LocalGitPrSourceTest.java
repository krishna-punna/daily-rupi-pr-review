package com.dailyrupi.prreview.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dailyrupi.prreview.diff.FileDiff;
import com.dailyrupi.prreview.diff.FileDiff.ChangeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs real git in a throwaway repository. */
class LocalGitPrSourceTest {

    @TempDir
    Path repo;

    private GitCli git;

    @BeforeEach
    void createRepository() throws IOException {
        git = new GitCli(repo);
        git.run("-c", "init.defaultBranch=main", "init", "--quiet");
        write("src/Service.java", "class Service {\n  int a() { return 1; }\n}\n");
        commit("base");
    }

    private void write(String path, String content) throws IOException {
        Path file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void commit(String message) throws IOException {
        git.run("add", "-A");
        git.run("-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false",
                "commit", "--quiet", "-m", message);
    }

    @Test
    void snapshotShowsOnlyWhatTheBranchChanges() throws IOException {
        git.run("checkout", "--quiet", "-b", "feature");
        write("src/Service.java", "class Service {\n  int a() { return 1; }\n  int b() { return 2; }\n}\n");
        write("src/New.java", "class New {}\n");
        commit("feature work");
        String featureSha = git.commitSha("HEAD");

        // A later commit on main must not show up as part of the feature's diff.
        git.run("checkout", "--quiet", "main");
        write("README.md", "unrelated\n");
        commit("main moves on");

        PullRequestSnapshot pr = new LocalGitPrSource(git).snapshot("local/repo", "7", "main", "feature");

        assertThat(pr.headSha()).isEqualTo(featureSha);
        assertThat(pr.baseSha()).isEqualTo(git.commitSha("main"));
        assertThat(pr.diffBaseSha()).isNotEqualTo(pr.baseSha());
        assertThat(pr.files()).extracting(FileDiff::path).containsExactlyInAnyOrder("src/Service.java", "src/New.java");
        assertThat(pr.changedLines().lines("src/Service.java")).containsExactly(3);
        assertThat(pr.files()).filteredOn(f -> f.path().equals("src/New.java"))
                .extracting(FileDiff::changeType).containsExactly(ChangeType.ADDED);
    }

    @Test
    void currentHeadFollowsTheBranchSoAStaleAnalysisCanBeDetected() throws IOException {
        git.run("checkout", "--quiet", "-b", "feature");
        write("src/Service.java", "class Service {}\n");
        commit("first");
        LocalGitPrSource source = new LocalGitPrSource(git);
        String analysed = source.snapshot("local/repo", "7", "main", "feature").headSha();

        write("src/Service.java", "class Service { }\n");
        commit("second");

        assertThat(source.currentHead("feature")).isNotEqualTo(analysed);
    }

    @Test
    void unknownRefIsAnError() {
        assertThatThrownBy(() -> new LocalGitPrSource(git).snapshot("local/repo", "7", "main", "no-such-branch"))
                .isInstanceOf(IOException.class).hasMessageContaining("rev-parse");
    }
}
