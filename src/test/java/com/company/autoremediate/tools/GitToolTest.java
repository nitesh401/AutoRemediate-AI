package com.company.autoremediate.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.autoremediate.TestProps;
import com.company.autoremediate.security.SecretRedactor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses a real local git repository: clone, isolated branch, commit, revert, push guards. */
class GitToolTest {
    private final CommandRunner runner = new CommandRunner(SecretRedactor.of(List.of()));
    private final GitTool git = new GitTool(runner, TestProps.defaults());

    private void sh(Path dir, String... args) {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t",
                "-c", "commit.gpgsign=false"));
        cmd.addAll(List.of(args));
        assertThat(runner.run(cmd, dir, Map.of(), Duration.ofSeconds(30)).success()).isTrue();
    }

    private Path origin(Path root) throws Exception {
        Path origin = Files.createDirectories(root.resolve("origin"));
        sh(origin, "init", "-b", "main");
        Files.writeString(origin.resolve("A.java"), "class A {}\n");
        sh(origin, "add", "-A");
        sh(origin, "commit", "-m", "init");
        return origin;
    }

    @Test
    void cloneBranchCommitAndRevert(@TempDir Path root) throws Exception {
        Path origin = origin(root);
        Path repo = root.resolve("work/repo");
        git.cloneRepository(origin.toString(), null, repo);
        assertThat(git.currentBranch(repo)).isEqualTo("main");

        git.createBranch(repo, "ai/remediation/job-1");
        assertThat(git.currentBranch(repo)).isEqualTo("ai/remediation/job-1");
        String checkpoint = git.headCommit(repo);

        Files.writeString(repo.resolve("A.java"), "class A { int x; }\n");
        Files.createDirectories(repo.resolve("target"));
        Files.writeString(repo.resolve("target/build.txt"), "artifact");
        PatchStats stats = git.stats(repo);
        assertThat(stats.files()).containsExactly("A.java"); // target/ is excluded
        assertThat(stats.changedLines()).isEqualTo(2);
        assertThat(git.stagedDiff(repo)).contains("+class A { int x; }");

        git.revertTo(repo, checkpoint);
        assertThat(Files.readString(repo.resolve("A.java"))).isEqualTo("class A {}\n");

        Files.writeString(repo.resolve("A.java"), "class A { int y; }\n");
        String commit = git.commitAll(repo, "fix: test");
        assertThat(commit).isNotEqualTo(checkpoint);
        // the origin's main branch was never touched
        assertThat(Files.readString(origin.resolve("A.java"))).isEqualTo("class A {}\n");
    }

    @Test
    void refusesBranchesOutsideRemediationPrefixAndProtectedPushes(@TempDir Path root) throws Exception {
        Path repo = root.resolve("work/repo");
        git.cloneRepository(origin(root).toString(), null, repo);
        assertThatThrownBy(() -> git.createBranch(repo, "main2")).isInstanceOf(GitException.class);
        assertThatThrownBy(() -> git.push(repo, "main")).isInstanceOf(GitException.class);
        assertThatThrownBy(() -> git.push(repo, "ai/remediation/job-1")).isInstanceOf(GitException.class); // no token
        assertThatThrownBy(() -> git.revertTo(repo, "--hard")).isInstanceOf(GitException.class);
    }
}
