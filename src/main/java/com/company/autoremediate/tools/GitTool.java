package com.company.autoremediate.tools;

import com.company.autoremediate.config.RemediationProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Git isolation: the agent only ever works on a fresh clone and on a branch under the configured
 * prefix (default ai/remediation/). Protected branches can never be pushed to.
 */
@Component
public class GitTool {
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final Pattern SAFE_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");
    private static final Pattern COMMIT = Pattern.compile("^[0-9a-f]{7,40}$");

    private final CommandRunner runner;
    private final RemediationProperties props;

    public GitTool(CommandRunner runner, RemediationProperties props) {
        this.runner = runner;
        this.props = props;
    }

    public void cloneRepository(String url, String branch, Path target) {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new GitException("Cannot create workspace: " + e.getMessage());
        }
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "protocol.ext.allow=never", "clone", "--single-branch"));
        if (branch != null && !branch.isBlank()) {
            requireSafeRef(branch);
            cmd.add("--branch");
            cmd.add(branch);
        }
        cmd.add("--");
        cmd.add(url);
        cmd.add(target.toString());
        ok(runner.run(cmd, target.getParent(), Map.of(), TIMEOUT), "git clone");
        excludeBuildArtifacts(target);
    }

    /** Keeps build output out of commits without touching the repository's own .gitignore. */
    private void excludeBuildArtifacts(Path repo) {
        try {
            Path exclude = repo.resolve(".git/info/exclude");
            Files.createDirectories(exclude.getParent());
            Files.writeString(exclude, "\ntarget/\n.scannerwork/\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new GitException("Cannot write .git/info/exclude: " + e.getMessage());
        }
    }

    public String currentBranch(Path repo) {
        return git(repo, "rev-parse", "--abbrev-ref", "HEAD").trim();
    }

    public String headCommit(Path repo) {
        return git(repo, "rev-parse", "HEAD").trim();
    }

    public String remoteUrl(Path repo) {
        return git(repo, "config", "--get", "remote.origin.url").trim();
    }

    public void createBranch(Path repo, String branch) {
        requireSafeRef(branch);
        if (!branch.startsWith(props.git().branchPrefix())) {
            throw new GitException("Refusing to create branch outside '" + props.git().branchPrefix() + "': " + branch);
        }
        git(repo, "checkout", "-b", branch);
    }

    /** Stages everything and returns which files changed and how many lines. */
    public PatchStats stats(Path repo) {
        git(repo, "add", "-A");
        String numstat = git(repo, "diff", "--cached", "--numstat");
        List<String> files = new ArrayList<>();
        int lines = 0;
        for (String line : numstat.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) continue;
            files.add(parts[2]);
            lines += parseCount(parts[0]) + parseCount(parts[1]);
        }
        return new PatchStats(files, lines);
    }

    public String stagedDiff(Path repo) {
        git(repo, "add", "-A");
        return git(repo, "diff", "--cached");
    }

    public String commitAll(Path repo, String message) {
        git(repo, "add", "-A");
        ok(runner.run(List.of("git",
                "-c", "user.name=" + props.git().authorName(),
                "-c", "user.email=" + props.git().authorEmail(),
                "-c", "commit.gpgsign=false",
                "commit", "--no-verify", "-m", message), repo, Map.of(), TIMEOUT), "git commit");
        return headCommit(repo);
    }

    /** Discards every uncommitted change and returns the work tree to the checkpoint. */
    public void revertTo(Path repo, String commit) {
        if (!COMMIT.matcher(commit).matches()) throw new GitException("Invalid commit id: " + commit);
        git(repo, "reset", "--hard", commit);
        git(repo, "clean", "-fd");
    }

    public void push(Path repo, String branch) {
        requireSafeRef(branch);
        if (!branch.startsWith(props.git().branchPrefix())) {
            throw new GitException("Refusing to push branch outside '" + props.git().branchPrefix() + "'");
        }
        if (props.git().protectedBranches().contains(branch)) {
            throw new GitException("Refusing to push protected branch: " + branch);
        }
        String token = props.git().token();
        if (token == null || token.isBlank()) throw new GitException("No git token configured");
        String basic = Base64.getEncoder()
                .encodeToString(("x-access-token:" + token).getBytes(StandardCharsets.UTF_8));
        ok(runner.run(List.of("git", "-c", "http.extraheader=Authorization: Basic " + basic,
                "push", "--set-upstream", "origin", branch), repo, Map.of(), TIMEOUT), "git push");
    }

    private String git(Path repo, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        CommandResult r = runner.run(cmd, repo, Map.of(), TIMEOUT);
        ok(r, "git " + args[0]);
        return r.output();
    }

    private static void ok(CommandResult r, String what) {
        if (!r.success()) {
            throw new GitException(what + " failed (exit " + r.exitCode() + "): " + tail(r.output()));
        }
    }

    private static String tail(String s) {
        return s.length() > 2000 ? s.substring(s.length() - 2000) : s;
    }

    private static int parseCount(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0; // binary files report '-'
        }
    }

    private static void requireSafeRef(String ref) {
        if (!SAFE_REF.matcher(ref).matches() || ref.contains("..")) {
            throw new GitException("Unsafe git ref: " + ref);
        }
    }
}
