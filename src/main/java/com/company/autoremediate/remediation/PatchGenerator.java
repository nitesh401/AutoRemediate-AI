package com.company.autoremediate.remediation;

import com.company.autoremediate.agent.RemediationAgent;
import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.model.Finding;
import com.company.autoremediate.model.FindingSource;
import com.company.autoremediate.model.PatchResult;
import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.SnykRemediationPlan;
import com.company.autoremediate.model.SonarIssue;
import com.company.autoremediate.model.SonarRemediationPlan;
import com.company.autoremediate.tools.ContextRetriever;
import com.company.autoremediate.tools.ContextRetriever.SourceContext;
import com.company.autoremediate.tools.GitTool;
import com.company.autoremediate.tools.PatchStats;
import com.company.autoremediate.tools.RepositoryTool;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Turns a finding into an applied, policy-checked patch in the isolated work tree.
 * Sonar: LLM plan -> validated search/replace edits. Snyk: LLM sanity check -> deterministic pom edit
 * to the scanner-recommended version only.
 */
@Component
public class PatchGenerator {

    /** notAutomatable = stop retrying this finding and report it for human review. */
    public record Generation(PatchResult patch, boolean notAutomatable, String message) {
        static Generation retryable(String error) {
            return new Generation(PatchResult.failure(error), false, error);
        }

        static Generation notAutomatable(String reason) {
            return new Generation(PatchResult.failure(reason), true, reason);
        }
    }

    private final RemediationAgent agent;
    private final ContextRetriever retriever;
    private final PatchApplier applier;
    private final PomVersionUpdater pomUpdater;
    private final RemediationPolicy policy;
    private final GitTool git;
    private final RemediationProperties props;

    public PatchGenerator(RemediationAgent agent, ContextRetriever retriever, PatchApplier applier,
                          PomVersionUpdater pomUpdater, RemediationPolicy policy, GitTool git,
                          RemediationProperties props) {
        this.agent = agent;
        this.retriever = retriever;
        this.applier = applier;
        this.pomUpdater = pomUpdater;
        this.policy = policy;
        this.git = git;
        this.props = props;
    }

    public Generation generate(Path repo, RepositoryTool repoTool, Finding finding, String feedback) {
        return switch (finding) {
            case SonarIssue s -> sonar(repo, repoTool, s, feedback);
            case SnykIssue s -> snyk(repo, repoTool, s, feedback);
        };
    }

    private Generation sonar(Path repo, RepositoryTool repoTool, SonarIssue issue, String feedback) {
        Optional<SourceContext> ctx = retriever.forSonar(repoTool, issue, props.ai().maxContextChars());
        if (ctx.isEmpty()) return Generation.notAutomatable("Source file not found in repository: " + issue.file());

        SonarRemediationPlan plan = agent.planSonar(issue, ctx.get(), feedback);
        if (!plan.safeToAutomate()) {
            return Generation.notAutomatable("Model judged the finding unsafe to automate: " + plan.rootCause());
        }
        String editError = policy.validateEdits(plan.editsOrEmpty());
        if (editError != null) return Generation.retryable(editError);

        PatchApplier.Result applied = applier.apply(repo, plan.editsOrEmpty());
        if (!applied.ok()) return Generation.retryable(applied.error());
        return finish(repo, FindingSource.SONAR);
    }

    private Generation snyk(Path repo, RepositoryTool repoTool, SnykIssue issue, String feedback) {
        String pom = repoTool.readFile("pom.xml");
        if (pom.length() > 20_000) pom = pom.substring(0, 20_000) + "\n... [truncated]";
        SnykRemediationPlan plan = agent.planSnyk(issue, pom, feedback);
        if (!plan.safeToAutomate()) {
            return Generation.notAutomatable("Model judged the upgrade unsafe to automate: " + plan.reasoning());
        }
        if (!issue.recommendedVersion().equals(plan.targetVersion())) {
            return Generation.retryable("Only the scanner-recommended version " + issue.recommendedVersion()
                    + " is acceptable, but the model proposed " + plan.targetVersion());
        }
        int idx = issue.packageName().indexOf(':');
        if (idx < 0) return Generation.notAutomatable("Unexpected package name: " + issue.packageName());
        PomVersionUpdater.Result updated = pomUpdater.updateDirectDependency(repo,
                issue.packageName().substring(0, idx), issue.packageName().substring(idx + 1),
                issue.recommendedVersion());
        if (!updated.ok()) return Generation.notAutomatable(updated.error());
        return finish(repo, FindingSource.SNYK);
    }

    private Generation finish(Path repo, FindingSource source) {
        PatchStats stats = git.stats(repo);
        String error = policy.validatePatch(source, stats);
        if (error != null) return Generation.retryable(error);
        String diff = git.stagedDiff(repo);
        return new Generation(new PatchResult(true, List.copyOf(stats.files()), stats.changedLines(), diff, null),
                false, "applied");
    }
}
