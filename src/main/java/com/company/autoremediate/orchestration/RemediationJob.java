package com.company.autoremediate.orchestration;

import com.company.autoremediate.model.FindingSource;
import com.company.autoremediate.model.IssueOutcome;
import com.company.autoremediate.model.JobStatus;
import com.company.autoremediate.model.RemediationRequest;
import com.company.autoremediate.model.Snapshot;
import com.company.autoremediate.verification.SnykComparator;
import com.company.autoremediate.verification.SonarComparator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RemediationJob {
    private final String id;
    private final RemediationRequest request;
    private final Instant createdAt = Instant.now();
    private final AuditLog audit;
    private final List<IssueOutcome> outcomes = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    private volatile JobStatus status = JobStatus.CREATED;
    private volatile Instant finishedAt;
    private volatile String result = "IN_PROGRESS";
    private volatile String error;
    private volatile String baseBranch;
    private volatile String remediationBranch;
    private volatile Path repoPath;
    private volatile Snapshot baseline;
    private volatile Snapshot finalSnapshot;
    private volatile String pullRequestStatus = "NOT_ATTEMPTED";
    private volatile String pullRequestUrl;
    private volatile String model;
    private volatile List<String> promptVersions = List.of();

    public RemediationJob(String id, RemediationRequest request, AuditLog audit) {
        this.id = id;
        this.request = request;
        this.audit = audit;
    }

    public synchronized void transition(JobStatus next) {
        if (status == next) return;
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal job transition " + status + " -> " + next);
        }
        status = next;
        audit.record("STATE " + next);
    }

    public synchronized void fail(String reason) {
        error = audit.redact(reason);
        result = "FAILED";
        status = JobStatus.FAILED;
        finishedAt = Instant.now();
        audit.record("JOB FAILED: " + reason);
    }

    public synchronized void complete(String resultLabel) {
        result = resultLabel;
        finishedAt = Instant.now();
        transition(JobStatus.COMPLETED);
        audit.record("JOB COMPLETE (" + resultLabel + ")");
    }

    public void log(String message) {
        audit.record(message);
    }

    public void warn(String message) {
        warnings.add(message);
        audit.record("WARNING " + message);
    }

    public void addOutcome(IssueOutcome outcome) {
        outcomes.add(outcome);
    }

    public long fixed(FindingSource source) {
        return outcomes.stream().filter(o -> "FIXED".equals(o.status()) && o.finding().source() == source).count();
    }

    /** New findings in the final accepted state relative to the original baseline (0 by construction of the gate). */
    public int newIssues() {
        if (baseline == null || finalSnapshot == null) return 0;
        int n = 0;
        if (baseline.sonarAvailable() && finalSnapshot.sonarAvailable()) {
            n += new SonarComparator().compare(baseline.sonarIssues(), finalSnapshot.sonarIssues()).newItems().size();
        }
        if (baseline.snykAvailable() && finalSnapshot.snykAvailable()) {
            n += new SnykComparator().compare(baseline.snykIssues(), finalSnapshot.snykIssues()).newItems().size();
        }
        return n;
    }

    public String id() { return id; }
    public RemediationRequest request() { return request; }
    public Instant createdAt() { return createdAt; }
    public Instant finishedAt() { return finishedAt; }
    public AuditLog audit() { return audit; }
    public List<IssueOutcome> outcomes() { return List.copyOf(outcomes); }
    public List<String> warnings() { return List.copyOf(warnings); }
    public JobStatus status() { return status; }
    public String result() { return result; }
    public String error() { return error; }
    public String baseBranch() { return baseBranch; }
    public void baseBranch(String v) { baseBranch = v; }
    public String remediationBranch() { return remediationBranch; }
    public void remediationBranch(String v) { remediationBranch = v; }
    public Path repoPath() { return repoPath; }
    public void repoPath(Path v) { repoPath = v; }
    public Snapshot baseline() { return baseline; }
    public void baseline(Snapshot v) { baseline = v; }
    public Snapshot finalSnapshot() { return finalSnapshot; }
    public void finalSnapshot(Snapshot v) { finalSnapshot = v; }
    public String pullRequestStatus() { return pullRequestStatus; }
    public void pullRequestStatus(String v) { pullRequestStatus = v; }
    public String pullRequestUrl() { return pullRequestUrl; }
    public void pullRequestUrl(String v) { pullRequestUrl = v; }
    public String model() { return model; }
    public void model(String v) { model = v; }
    public List<String> promptVersions() { return promptVersions; }
    public void promptVersions(List<String> v) { promptVersions = List.copyOf(v); }
}
