package com.company.autoremediate.orchestration;

import com.company.autoremediate.config.RemediationProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Filesystem layout: {root}/jobs/{jobId}/repo (isolated clone) and {root}/reports/{jobId}/. */
@Component
public class Workspace {
    private final Path root;

    public Workspace(RemediationProperties props) {
        this.root = Path.of(props.workspaceRoot()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path repoDir(String jobId) {
        return root.resolve("jobs").resolve(jobId).resolve("repo");
    }

    public Path reportDir(String jobId) {
        return root.resolve("reports").resolve(jobId);
    }
}
