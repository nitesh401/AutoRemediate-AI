package com.company.autoremediate.security;

import com.company.autoremediate.config.RemediationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Validates user-supplied repository URLs/branches before they ever reach the git command line. */
@Component
public class RepositoryUrlValidator {
    private static final Pattern HTTPS = Pattern.compile("^https://[A-Za-z0-9.-]+(:\\d{1,5})?/[A-Za-z0-9._~/-]+/?$");
    private static final Pattern SSH = Pattern.compile("^git@[A-Za-z0-9.-]+:[A-Za-z0-9._~/-]+$");
    private static final Pattern BRANCH = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");

    private final boolean allowLocal;

    public RepositoryUrlValidator(RemediationProperties props) {
        this.allowLocal = props.allowLocalRepositories();
    }

    /** Returns an error message, or null when the URL is acceptable. */
    public String validateUrl(String url) {
        if (url == null || url.isBlank()) return "repositoryUrl is required";
        if (url.startsWith("-") || url.contains("::") || url.contains("\n") || url.contains(" ")) {
            return "Unsupported repository URL";
        }
        if (HTTPS.matcher(url).matches() || SSH.matcher(url).matches()) return null;
        if (allowLocal && Path.of(url).isAbsolute()) {
            Path p = Path.of(url);
            if (Files.isDirectory(p) && (Files.exists(p.resolve(".git")) || Files.exists(p.resolve("HEAD")))) return null;
            return "Local path is not a git repository";
        }
        return "Only https:// and git@host:path URLs are accepted"
                + (allowLocal ? " (or an absolute path to a local git repository)" : "");
    }

    public String validateBranch(String branch) {
        if (branch == null || branch.isBlank()) return null;
        return BRANCH.matcher(branch).matches() && !branch.contains("..") ? null : "Invalid branch name";
    }
}
