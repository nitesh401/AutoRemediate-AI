package com.company.autoremediate.tools;

import java.util.List;
import java.util.Optional;

/** Read-only view of the repository workspace. Never send the whole repository to the LLM. */
public interface RepositoryTool {

    record MavenModule(String path, String groupId, String artifactId, String version) {}

    record RepositoryMetadata(String name, String javaVersion, String springBootVersion,
                              boolean mavenWrapper, List<MavenModule> modules) {}

    record CodeMatch(String file, int line, String text) {}

    RepositoryMetadata inspectRepository();

    String readFile(String path);

    List<String> listFiles(String directory);

    List<CodeMatch> searchCode(String query);

    List<MavenModule> discoverModules();

    /** Resolves a scanner-reported path to a real repo-relative path (handles multi-module prefixes). */
    Optional<String> resolveFile(String reportedPath);
}
