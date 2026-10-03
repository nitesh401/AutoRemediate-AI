package com.company.autoremediate.orchestration;

import com.company.autoremediate.model.FindingSource;
import com.company.autoremediate.model.Snapshot;
import java.util.List;

/** API representation of a job (matches the design's status response, plus a few extras). */
public record JobView(
        String jobId,
        String status,
        String result,
        long sonarFixed,
        long snykFixed,
        String build,
        String tests,
        int newIssues,
        String pullRequest,
        String pullRequestUrl,
        String remediationBranch,
        String error,
        List<String> warnings) {

    public static JobView of(RemediationJob job) {
        Snapshot snap = job.finalSnapshot() != null ? job.finalSnapshot() : job.baseline();
        String build = snap == null ? "UNKNOWN" : snap.buildPassed() ? "PASS" : "FAIL";
        String tests = snap == null || snap.tests() == null ? "UNKNOWN" : snap.tests().passed() ? "PASS" : "FAIL";
        return new JobView(job.id(), job.status().name(), job.result(),
                job.fixed(FindingSource.SONAR), job.fixed(FindingSource.SNYK),
                build, tests, job.newIssues(), job.pullRequestStatus(), job.pullRequestUrl(),
                job.remediationBranch(), job.error(), job.warnings());
    }
}
