package com.company.autoremediate.orchestration;

import com.company.autoremediate.agent.LlmClient;
import com.company.autoremediate.agent.PromptTemplates;
import com.company.autoremediate.agent.ReviewAgent;
import com.company.autoremediate.agent.VerificationAgent;
import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.model.AttemptRecord;
import com.company.autoremediate.model.BuildResult;
import com.company.autoremediate.model.ClassifiedFinding;
import com.company.autoremediate.model.Confidence;
import com.company.autoremediate.model.Finding;
import com.company.autoremediate.model.IssueOutcome;
import com.company.autoremediate.model.JobStatus;
import com.company.autoremediate.model.PatchResult;
import com.company.autoremediate.model.RemediationRequest;
import com.company.autoremediate.model.ReviewDecision;
import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.Snapshot;
import com.company.autoremediate.model.SonarIssue;
import com.company.autoremediate.model.TestSummary;
import com.company.autoremediate.model.VerificationResult;
import com.company.autoremediate.remediation.IssueClassifier;
import com.company.autoremediate.remediation.PatchGenerator;
import com.company.autoremediate.remediation.RemediationPolicy;
import com.company.autoremediate.report.ReportGenerator;
import com.company.autoremediate.tools.BuildTool;
import com.company.autoremediate.tools.FileSystemRepositoryTool;
import com.company.autoremediate.tools.GitTool;
import com.company.autoremediate.tools.PullRequestTool;
import com.company.autoremediate.tools.RepositoryTool;
import com.company.autoremediate.tools.SnykScan;
import com.company.autoremediate.tools.SnykTool;
import com.company.autoremediate.tools.SonarScan;
import com.company.autoremediate.tools.SonarTool;
import com.company.autoremediate.tools.TestTool;
import com.company.autoremediate.verification.VerificationEngine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Drives the job state machine. The LLM proposes patches; every pass/fail decision here comes from
 * deterministic tools (Maven, test reports, Sonar/Snyk rescans, VerificationEngine).
 *
 * Isolation: a fresh clone, a branch under ai/remediation/, one commit per verified fix.
 * Failed attempts are reset to the checkpoint. Nothing is ever pushed to or merged into a base branch.
 */
@Component
public class RemediationOrchestrator {
    private final RemediationProperties props;
    private final Workspace workspace;
    private final GitTool git;
    private final BuildTool buildTool;
    private final TestTool testTool;
    private final SonarTool sonarTool;
    private final SnykTool snykTool;
    private final IssueClassifier classifier;
    private final RemediationPolicy policy;
    private final PatchGenerator patchGenerator;
    private final ReviewAgent reviewAgent;
    private final VerificationAgent verificationAgent;
    private final VerificationEngine engine;
    private final ReportGenerator reports;
    private final PullRequestTool pullRequestTool;
    private final LlmClient llm;
    private final PromptTemplates prompts;

    public RemediationOrchestrator(RemediationProperties props, Workspace workspace, GitTool git,
                                   BuildTool buildTool, TestTool testTool, SonarTool sonarTool,
                                   SnykTool snykTool, IssueClassifier classifier, RemediationPolicy policy,
                                   PatchGenerator patchGenerator, ReviewAgent reviewAgent,
                                   VerificationAgent verificationAgent, VerificationEngine engine,
                                   ReportGenerator reports, PullRequestTool pullRequestTool,
                                   LlmClient llm, PromptTemplates prompts) {
        this.props = props;
        this.workspace = workspace;
        this.git = git;
        this.buildTool = buildTool;
        this.testTool = testTool;
        this.sonarTool = sonarTool;
        this.snykTool = snykTool;
        this.classifier = classifier;
        this.policy = policy;
        this.patchGenerator = patchGenerator;
        this.reviewAgent = reviewAgent;
        this.verificationAgent = verificationAgent;
        this.engine = engine;
        this.reports = reports;
        this.pullRequestTool = pullRequestTool;
        this.llm = llm;
        this.prompts = prompts;
    }

    private record FixResult(IssueOutcome outcome, Snapshot snapshot) {}

    public void run(RemediationJob job) {
        try {
            execute(job);
        } catch (Exception e) {
            job.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            reports.write(job);
        }
    }

    private void execute(RemediationJob job) {
        RemediationRequest req = job.request();
        job.model(llm.modelName());
        job.promptVersions(prompts.versions());
        Path repo = workspace.repoDir(job.id());
        job.repoPath(repo);

        // 1. isolated workspace
        job.transition(JobStatus.CLONING);
        git.cloneRepository(req.repositoryUrl(), req.branch(), repo);
        job.baseBranch(git.currentBranch(repo));
        String branch = props.git().branchPrefix() + job.id();
        git.createBranch(repo, branch);
        job.remediationBranch(branch);
        job.log("Repository cloned; base branch " + job.baseBranch() + ", working branch " + branch);

        // 2. baseline build + tests
        job.transition(JobStatus.BASELINE);
        BuildResult baseBuild = buildTool.build(repo);
        TestSummary baseTests = testTool.summarize(repo);
        job.log("Baseline build " + (baseBuild.success() ? "PASS" : "FAIL") + ", tests run " + baseTests.run()
                + ", failures " + (baseTests.failures() + baseTests.errors()));
        if (!baseBuild.success()) {
            job.baseline(Snapshot.failedBuild(baseTests));
            job.fail("BASELINE_FAILURE: the unmodified repository does not build/test successfully "
                    + baseTests.failedTests() + ". Fix the baseline first; the agent will not claim its changes caused this.");
            return;
        }

        // 3. scans
        job.transition(JobStatus.SCANNING);
        SonarScan sonar = sonarTool.isEnabled() ? sonarTool.scan(repo) : null;
        SnykScan snyk = snykTool.isEnabled() ? snykTool.scan(repo) : null;
        if (sonar != null && !sonar.ok()) job.warn("Sonar baseline scan failed: " + sonar.error());
        if (snyk != null && !snyk.ok()) job.warn("Snyk baseline scan failed: " + snyk.error());
        if (sonar == null) job.log("Sonar disabled");
        if (snyk == null) job.log("Snyk disabled");
        boolean sonarOk = sonar != null && sonar.ok();
        boolean snykOk = snyk != null && snyk.ok();
        Snapshot baseline = new Snapshot(true, baseTests,
                sonarOk ? sonar.issues() : List.of(), sonarOk,
                snykOk ? snyk.issues() : List.of(), snykOk);
        job.baseline(baseline);
        job.finalSnapshot(baseline);
        job.log("Baseline findings: Sonar " + baseline.sonarIssues().size() + ", Snyk " + baseline.snykIssues().size());

        // 4. classify + select
        job.transition(JobStatus.CLASSIFYING);
        List<ClassifiedFinding> classified = classifier.classify(baseline.sonarIssues(), baseline.snykIssues());
        int limit = req.maxIssues() != null ? req.maxIssues() : props.maxIssues();
        List<ClassifiedFinding> selected = new ArrayList<>(policy.select(classified, limit));
        job.log("Classified " + classified.size() + " finding(s); " + selected.size() + " selected for automation");

        for (ClassifiedFinding cf : classified) {
            if (!selected.contains(cf)) {
                String status = cf.confidence() == Confidence.HIGH_CONFIDENCE
                        ? "SKIPPED_NOT_SELECTED" : "SKIPPED_NOT_AUTOMATABLE";
                job.addOutcome(outcome(cf, status, cf.reason(), List.of(), null, List.of()));
            }
        }
        if (!selected.isEmpty() && !llm.isConfigured()) {
            job.warn("LLM is not configured; running in scan-only mode (no fixes attempted)");
            selected.forEach(cf -> job.addOutcome(
                    outcome(cf, "SKIPPED_LLM_UNAVAILABLE", "LLM not configured", List.of(), null, List.of())));
            selected.clear();
        }

        // 5. remediate one finding at a time
        Snapshot current = baseline;
        for (ClassifiedFinding cf : selected) {
            FixResult result = remediate(job, repo, cf, current);
            job.addOutcome(result.outcome());
            if (result.snapshot() != null) {
                current = result.snapshot();
                job.finalSnapshot(current);
            }
        }

        // 6. report, optional PR
        job.transition(JobStatus.REPORTING);
        long fixed = job.outcomes().stream().filter(o -> "FIXED".equals(o.status())).count();
        reports.write(job);
        handlePullRequest(job, repo, fixed);

        String label;
        if (classified.isEmpty()) label = "NO_FINDINGS";
        else if (fixed == 0) label = "NO_CHANGES";
        else label = fixed == selected.size() ? "FIXED" : "PARTIALLY_FIXED";
        job.complete(label);
    }

    private FixResult remediate(RemediationJob job, Path repo, ClassifiedFinding cf, Snapshot current) {
        Finding finding = cf.finding();
        if (!stillPresent(finding, current)) {
            job.log("Skipping " + finding.id() + ": already resolved by an earlier fix");
            return new FixResult(outcome(cf, "SKIPPED_ALREADY_RESOLVED", "Resolved by an earlier fix",
                    List.of(), null, List.of()), null);
        }
        String checkpoint = git.headCommit(repo);
        git.revertTo(repo, checkpoint); // start every issue from a clean tree
        RepositoryTool repoTool = new FileSystemRepositoryTool(repo);
        List<AttemptRecord> attempts = new ArrayList<>();
        String feedback = "";
        int maxAttempts = Math.max(1, props.maxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            job.transition(JobStatus.REMEDIATING);
            job.log("Issue " + finding.describe() + " - attempt " + attempt + "/" + maxAttempts);
            String buildOutput = "";
            try {
                PatchGenerator.Generation gen = patchGenerator.generate(repo, repoTool, finding, feedback);
                if (gen.notAutomatable()) {
                    attempts.add(new AttemptRecord(attempt, "NOT_AUTOMATABLE", List.of(gen.message()), ""));
                    git.revertTo(repo, checkpoint);
                    job.transition(JobStatus.RETRY);
                    job.transition(JobStatus.REVERTED);
                    return new FixResult(outcome(cf, "SKIPPED_NOT_AUTOMATABLE", gen.message(), attempts, null, List.of()), null);
                }
                PatchResult patch = gen.patch();
                if (!patch.applied()) {
                    feedback = patch.error();
                    attempts.add(new AttemptRecord(attempt, "PATCH_REJECTED", List.of(patch.error()), ""));
                    git.revertTo(repo, checkpoint);
                    toRetry(job);
                    continue;
                }

                ReviewDecision review = reviewAgent.review(finding.describe(), patch.diff());
                if (!review.approved()) {
                    List<String> reasons = review.reasons() == null ? List.of("Rejected by reviewer") : review.reasons();
                    feedback = "Reviewer rejected the patch: " + String.join("; ", reasons);
                    attempts.add(new AttemptRecord(attempt, "REVIEW_REJECTED", reasons, patch.diff()));
                    git.revertTo(repo, checkpoint);
                    toRetry(job);
                    continue;
                }

                // deterministic verification
                job.transition(JobStatus.BUILDING);
                BuildResult build = buildTool.build(repo);
                buildOutput = build.output();
                Snapshot post;
                if (!build.success()) {
                    post = Snapshot.failedBuild(testTool.summarize(repo));
                } else {
                    job.transition(JobStatus.TESTING);
                    TestSummary tests = testTool.summarize(repo);
                    if (!tests.passed()) {
                        post = new Snapshot(true, tests, List.of(), false, List.of(), false);
                    } else {
                        job.transition(JobStatus.RESCANNING);
                        SonarScan sonar = current.sonarAvailable() ? sonarTool.scan(repo) : null;
                        SnykScan snyk = current.snykAvailable() ? snykTool.scan(repo) : null;
                        boolean sonarOk = sonar != null && sonar.ok();
                        boolean snykOk = snyk != null && snyk.ok();
                        post = new Snapshot(true, tests, sonarOk ? sonar.issues() : List.of(), sonarOk,
                                snykOk ? snyk.issues() : List.of(), snykOk);
                    }
                }
                job.transition(JobStatus.VERIFYING);
                VerificationResult vr = engine.verify(current, post, finding);
                job.log("Verification " + vr.decision() + (vr.reasons().isEmpty() ? "" : " " + vr.reasons()));

                if (vr.safe()) {
                    String commit = git.commitAll(repo, commitMessage(finding));
                    attempts.add(new AttemptRecord(attempt, "VERIFIED", List.of(), patch.diff()));
                    job.log("Issue " + finding.id() + " RESOLVED; commit " + commit);
                    return new FixResult(outcome(cf, "FIXED", "", attempts, commit, patch.changedFiles()), post);
                }

                String diagnosis = verificationAgent.diagnose(finding.describe(), vr, buildOutput);
                feedback = String.join("; ", vr.reasons()) + (diagnosis.isBlank() ? "" : " Diagnosis: " + diagnosis);
                attempts.add(new AttemptRecord(attempt, "REJECTED_BY_GATE", vr.reasons(), patch.diff()));
                git.revertTo(repo, checkpoint);
                toRetry(job);
            } catch (RuntimeException e) {
                String msg = job.audit().redact(e.getClass().getSimpleName() + ": " + e.getMessage());
                job.log("Attempt " + attempt + " errored: " + msg);
                feedback = msg;
                attempts.add(new AttemptRecord(attempt, "ERROR", List.of(msg), ""));
                try {
                    git.revertTo(repo, checkpoint);
                } catch (RuntimeException revertError) {
                    throw new IllegalStateException("Could not revert after failure: " + revertError.getMessage(), e);
                }
                toRetry(job);
            }
        }

        job.transition(JobStatus.REVERTED);
        job.log("Issue " + finding.id() + " could not be remediated after " + maxAttempts + " attempt(s); reverted");
        return new FixResult(outcome(cf, "FAILED_TO_REMEDIATE",
                "All " + maxAttempts + " attempts failed; changes reverted. " + feedback,
                attempts, null, List.of()), null);
    }

    private void toRetry(RemediationJob job) {
        JobStatus s = job.status();
        if (s != JobStatus.REMEDIATING && s != JobStatus.VERIFYING && s != JobStatus.RETRY) {
            job.transition(JobStatus.VERIFYING);
        }
        job.transition(JobStatus.RETRY);
    }

    private void handlePullRequest(RemediationJob job, Path repo, long fixed) {
        if (!job.request().wantsPullRequest()) {
            job.pullRequestStatus("SKIPPED_NOT_REQUESTED");
        } else if (fixed == 0) {
            job.pullRequestStatus("SKIPPED_NO_CHANGES");
        } else if (!props.git().pushEnabled()) {
            job.pullRequestStatus("SKIPPED_PUSH_DISABLED");
        } else if (props.git().token() == null || props.git().token().isBlank()) {
            job.pullRequestStatus("SKIPPED_NO_TOKEN");
        } else {
            try {
                String remote = git.remoteUrl(repo);
                if (PullRequestTool.githubSlug(remote).isEmpty()) {
                    job.pullRequestStatus("SKIPPED_UNSUPPORTED_REMOTE");
                } else {
                    git.push(repo, job.remediationBranch());
                    String body = reports.read(job.id(), "md").orElse("AutoRemediate AI remediation (see report).");
                    if (body.length() > 60_000) body = body.substring(0, 60_000) + "\n... [truncated]";
                    String url = pullRequestTool.createPullRequest(remote, job.remediationBranch(), job.baseBranch(),
                            "AutoRemediate AI: " + fixed + " verified fix(es)", body);
                    job.pullRequestUrl(url);
                    job.pullRequestStatus("CREATED");
                    job.transition(JobStatus.PR_CREATED);
                    job.log("Draft pull request created: " + url);
                }
            } catch (RuntimeException e) {
                job.pullRequestStatus("FAILED");
                job.warn("Pull request not created: " + job.audit().redact(e.getMessage()));
            }
        }
        job.log("Pull request: " + job.pullRequestStatus());
    }

    private static boolean stillPresent(Finding finding, Snapshot snapshot) {
        return switch (finding) {
            case SonarIssue s -> snapshot.sonarIssues().stream().anyMatch(i -> i.fingerprint().equals(s.fingerprint()));
            case SnykIssue s -> snapshot.snykIssues().stream().anyMatch(i -> i.fingerprint().equals(s.fingerprint()));
        };
    }

    private static String commitMessage(Finding finding) {
        String title = switch (finding) {
            case SonarIssue s -> "fix(sonar): " + s.rule() + " in " + s.file();
            case SnykIssue s -> "fix(snyk): upgrade " + s.packageName() + " " + s.currentVersion()
                    + " -> " + s.recommendedVersion();
        };
        return title + "\n\n" + finding.describe()
                + "\n\nAutomated by AutoRemediate AI. Verified by build, tests and scanner rescans before commit.";
    }

    private static IssueOutcome outcome(ClassifiedFinding cf, String status, String detail,
                                        List<AttemptRecord> attempts, String commit, List<String> files) {
        return new IssueOutcome(cf.finding(), cf.confidence(), cf.reason(), status,
                detail == null ? "" : detail, List.copyOf(attempts), commit, List.copyOf(files));
    }
}
