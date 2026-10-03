package com.company.autoremediate;

import com.company.autoremediate.config.RemediationProperties;
import java.util.List;

public final class TestProps {
    private TestProps() {}

    public static RemediationProperties defaults(String workspace, boolean allowLocal) {
        return new RemediationProperties(workspace, 3, 5, 1, allowLocal,
                new RemediationProperties.Build(List.of("mvn", "-B", "clean", "verify"), 60),
                new RemediationProperties.Sonar(false, "http://localhost:9000", "", "", "-autoremediate",
                        List.of("mvn", "sonar:sonar"), 60, 10),
                new RemediationProperties.Snyk(false, "", List.of("snyk", "test", "--json"), 60),
                new RemediationProperties.Ai("anthropic", "test-model", "", "https://api.anthropic.com",
                        "2023-06-01", null, 1000, 30, 60000),
                new RemediationProperties.Git("Test", "test@example.com", "ai/remediation/", false, "",
                        "https://api.github.com", List.of("main", "master", "develop")),
                new RemediationProperties.Policy(
                        List.of("java:S1128", "java:S3655"), List.of("java:S2095"),
                        List.of("auth", "security"), 2, 40, 5),
                new RemediationProperties.Api(""));
    }

    public static RemediationProperties defaults() {
        return defaults("./work", true);
    }
}
