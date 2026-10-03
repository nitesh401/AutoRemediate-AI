package com.company.autoremediate.model;

import java.util.List;

public record SnykIssue(
        String id,
        String packageName,
        String currentVersion,
        String severity,
        String vulnerability,
        String recommendedVersion,
        boolean directDependency,
        List<String> dependencyPath) implements Finding {

    @Override
    public FindingSource source() {
        return FindingSource.SNYK;
    }

    /** Version is deliberately excluded: the fix changes it. */
    @Override
    public String fingerprint() {
        return id + "|" + packageName;
    }

    @Override
    public String describe() {
        return "Snyk " + id + " (" + severity + ") " + packageName + "@" + currentVersion
                + " - " + vulnerability + (recommendedVersion != null ? " [fix: " + recommendedVersion + "]" : "");
    }
}
