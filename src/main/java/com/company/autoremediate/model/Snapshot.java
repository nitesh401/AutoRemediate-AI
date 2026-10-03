package com.company.autoremediate.model;

import java.util.List;

/** The measurable state of the repository at one point in time. */
public record Snapshot(
        boolean buildPassed,
        TestSummary tests,
        List<SonarIssue> sonarIssues,
        boolean sonarAvailable,
        List<SnykIssue> snykIssues,
        boolean snykAvailable) {

    public static Snapshot failedBuild(TestSummary tests) {
        return new Snapshot(false, tests, List.of(), false, List.of(), false);
    }
}
