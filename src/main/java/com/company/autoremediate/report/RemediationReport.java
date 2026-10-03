package com.company.autoremediate.report;

import com.company.autoremediate.model.IssueOutcome;
import java.time.Instant;
import java.util.List;

public record RemediationReport(
        String jobId,
        String repositoryUrl,
        String baseBranch,
        String remediationBranch,
        String status,
        String result,
        Instant startedAt,
        Instant finishedAt,
        String model,
        List<String> promptVersions,
        SnapshotSummary baseline,
        SnapshotSummary finalState,
        int newIssues,
        List<IssueOutcome> outcomes,
        String verificationDecision,
        String safetyStatement,
        String pullRequestStatus,
        String pullRequestUrl,
        String workspacePath,
        List<String> warnings,
        String error) {

    public record SnapshotSummary(
            boolean buildPassed,
            int testsRun,
            int testFailures,
            List<String> failedTests,
            boolean sonarAvailable,
            int sonarIssues,
            boolean snykAvailable,
            int snykVulnerabilities) {}
}
