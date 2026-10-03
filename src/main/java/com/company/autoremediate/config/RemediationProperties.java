package com.company.autoremediate.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All runtime configuration. Secrets (tokens, API keys) come from environment variables
 * via application.yml placeholders and are never hardcoded.
 */
@ConfigurationProperties(prefix = "remediation")
public record RemediationProperties(
        @DefaultValue("./work") String workspaceRoot,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("5") int maxIssues,
        @DefaultValue("1") int maxConcurrentJobs,
        @DefaultValue("true") boolean allowLocalRepositories,
        @DefaultValue Build build,
        @DefaultValue Sonar sonar,
        @DefaultValue Snyk snyk,
        @DefaultValue Ai ai,
        @DefaultValue Git git,
        @DefaultValue Policy policy,
        @DefaultValue Api api) {

    public record Build(
            @DefaultValue({"mvn", "-B", "-ntp", "clean", "verify"}) List<String> command,
            @DefaultValue("1800") int timeoutSeconds) {}

    public record Sonar(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("http://localhost:9000") String hostUrl,
            @DefaultValue("") String token,
            @DefaultValue("") String projectKey,
            @DefaultValue("-autoremediate") String projectKeySuffix,
            @DefaultValue({"mvn", "-B", "-ntp", "test-compile",
                    "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar"}) List<String> command,
            @DefaultValue("1800") int timeoutSeconds,
            @DefaultValue("300") int taskWaitSeconds) {}

    public record Snyk(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String token,
            @DefaultValue({"snyk", "test", "--json"}) List<String> command,
            @DefaultValue("900") int timeoutSeconds) {}

    /** temperature is optional (null = omitted) because some models reject sampling parameters. */
    public record Ai(
            @DefaultValue("anthropic") String provider,
            @DefaultValue("") String model,
            @DefaultValue("") String apiKey,
            @DefaultValue("https://api.anthropic.com") String baseUrl,
            @DefaultValue("2023-06-01") String anthropicVersion,
            Double temperature,
            @DefaultValue("4096") int maxTokens,
            @DefaultValue("180") int timeoutSeconds,
            @DefaultValue("60000") int maxContextChars) {}

    public record Git(
            @DefaultValue("AutoRemediate AI") String authorName,
            @DefaultValue("autoremediate-ai@localhost") String authorEmail,
            @DefaultValue("ai/remediation/") String branchPrefix,
            @DefaultValue("false") boolean pushEnabled,
            @DefaultValue("") String token,
            @DefaultValue("https://api.github.com") String apiUrl,
            @DefaultValue({"main", "master", "develop", "release"}) List<String> protectedBranches) {}

    public record Policy(
            @DefaultValue({"java:S1128", "java:S1481", "java:S1068", "java:S3655",
                    "java:S1155", "java:S1612", "java:S125"}) List<String> highConfidenceSonarRules,
            @DefaultValue({"java:S2095", "java:S2259", "java:S1874", "java:S1192",
                    "java:S1066"}) List<String> mediumConfidenceSonarRules,
            @DefaultValue({"auth", "security", "permission", "crypto", "password", "token",
                    "migration"}) List<String> humanReviewPathKeywords,
            @DefaultValue("2") int maxChangedFilesPerFix,
            @DefaultValue("40") int maxChangedLinesPerFix,
            @DefaultValue("5") int maxEditsPerPlan) {}

    public record Api(@DefaultValue("") String key) {}
}
