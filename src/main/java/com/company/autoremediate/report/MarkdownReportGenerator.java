package com.company.autoremediate.report;

import com.company.autoremediate.model.AttemptRecord;
import com.company.autoremediate.model.IssueOutcome;
import com.company.autoremediate.report.RemediationReport.SnapshotSummary;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministic Markdown rendering. Facts come from tools; no model text is trusted for claims of safety. */
@Component
public class MarkdownReportGenerator {

    public String generate(RemediationReport r) {
        StringBuilder md = new StringBuilder();
        md.append("# AutoRemediate AI - Remediation Report\n\n");
        md.append("- **Job:** ").append(r.jobId()).append('\n');
        md.append("- **Repository:** ").append(r.repositoryUrl()).append('\n');
        md.append("- **Base branch:** ").append(nz(r.baseBranch())).append('\n');
        md.append("- **Remediation branch:** ").append(nz(r.remediationBranch())).append('\n');
        md.append("- **Status:** ").append(r.status()).append(" (").append(r.result()).append(")\n");
        md.append("- **Model:** ").append(nz(r.model())).append(" | **Prompts:** ")
                .append(String.join(", ", r.promptVersions())).append("\n\n");

        md.append("## 1. Executive summary\n\n");
        long fixed = r.outcomes().stream().filter(o -> "FIXED".equals(o.status())).count();
        long humanReview = r.outcomes().stream().filter(o -> o.status().startsWith("SKIPPED")
                || "FAILED_TO_REMEDIATE".equals(o.status())).count();
        md.append(fixed).append(" finding(s) fixed and verified; ").append(humanReview)
                .append(" finding(s) skipped or not remediated; ").append(r.newIssues())
                .append(" new finding(s) in the final state.\n\n");
        md.append("**Verification decision:** ").append(r.verificationDecision()).append("\n\n");
        md.append("> ").append(r.safetyStatement()).append("\n\n");
        if (r.error() != null) md.append("**Error:** ").append(r.error()).append("\n\n");

        md.append("## 2. Issues detected\n\n");
        if (r.outcomes().isEmpty()) {
            md.append("None.\n\n");
        } else {
            md.append("| Finding | Confidence | Disposition |\n|---|---|---|\n");
            for (IssueOutcome o : r.outcomes()) {
                md.append("| ").append(esc(o.finding().describe())).append(" | ").append(o.confidence())
                        .append(" | ").append(o.status()).append(" |\n");
            }
            md.append('\n');
        }

        md.append("## 3. Issues automatically fixed\n\n");
        List<IssueOutcome> fixedList = r.outcomes().stream().filter(o -> "FIXED".equals(o.status())).toList();
        if (fixedList.isEmpty()) md.append("None.\n\n");
        for (IssueOutcome o : fixedList) {
            md.append("- ").append(o.finding().describe()).append(" (commit `").append(o.commit()).append("`)\n");
        }
        if (!fixedList.isEmpty()) md.append('\n');

        md.append("## 4. Issues skipped or not remediated\n\n");
        List<IssueOutcome> skipped = r.outcomes().stream().filter(o -> !"FIXED".equals(o.status())).toList();
        if (skipped.isEmpty()) md.append("None.\n\n");
        for (IssueOutcome o : skipped) {
            md.append("- ").append(o.finding().describe()).append(" - **").append(o.status()).append("**: ")
                    .append(nz(o.detail().isBlank() ? o.classificationReason() : o.detail())).append('\n');
            for (AttemptRecord a : o.attempts()) {
                md.append("    - attempt ").append(a.attempt()).append(": ").append(a.outcome());
                if (!a.reasons().isEmpty()) md.append(" - ").append(esc(String.join("; ", a.reasons())));
                md.append('\n');
            }
        }
        if (!skipped.isEmpty()) md.append('\n');

        md.append("## 5. Files changed\n\n");
        boolean any = false;
        for (IssueOutcome o : fixedList) {
            for (String f : o.changedFiles()) {
                md.append("- `").append(f).append("`\n");
                any = true;
            }
        }
        if (!any) md.append("None.\n");
        md.append('\n');

        md.append("## 6. Sonar before/after\n\n");
        md.append(compareLine("Sonar issues", r.baseline().sonarAvailable(), r.baseline().sonarIssues(),
                r.finalState().sonarAvailable(), r.finalState().sonarIssues())).append("\n\n");
        md.append("## 7. Snyk before/after\n\n");
        md.append(compareLine("Snyk vulnerabilities", r.baseline().snykAvailable(), r.baseline().snykVulnerabilities(),
                r.finalState().snykAvailable(), r.finalState().snykVulnerabilities())).append("\n\n");

        md.append("## 8. Build results\n\n");
        md.append("- Baseline: ").append(pass(r.baseline().buildPassed())).append('\n');
        md.append("- Final: ").append(pass(r.finalState().buildPassed())).append("\n\n");

        md.append("## 9. Test results\n\n");
        md.append("- Baseline: ").append(tests(r.baseline())).append('\n');
        md.append("- Final: ").append(tests(r.finalState())).append("\n\n");

        md.append("## 10. Verification decision\n\n").append(r.verificationDecision()).append("\n\n");

        md.append("## 11. Risks\n\n");
        md.append("- Automated checks cannot prove that business behavior is unchanged; they only show that no regression was detected within the available checks.\n");
        md.append("- Test coverage determines how much the passing test run means.\n\n");

        md.append("## 12. Human review requirements\n\n");
        md.append("- A human must review and approve the pull request. V1 never merges automatically.\n");
        for (IssueOutcome o : skipped) {
            md.append("- Review: ").append(o.finding().describe()).append(" (").append(o.status()).append(")\n");
        }
        md.append('\n');

        md.append("## Pull request\n\n- Status: ").append(r.pullRequestStatus());
        if (r.pullRequestUrl() != null) md.append(" - ").append(r.pullRequestUrl());
        md.append("\n- Work tree: `").append(nz(r.workspacePath())).append("`\n");
        if (!r.warnings().isEmpty()) {
            md.append("\n## Warnings\n\n");
            r.warnings().forEach(w -> md.append("- ").append(w).append('\n'));
        }
        return md.toString();
    }

    private static String compareLine(String label, boolean beforeOk, int before, boolean afterOk, int after) {
        if (!beforeOk) return label + ": not available (scanner disabled or failed on the baseline).";
        if (!afterOk) return label + ": " + before + " before; final scan not available.";
        int delta = after - before;
        return label + ": " + before + " -> " + after + " (" + (delta > 0 ? "+" : "") + delta + ")";
    }

    private static String tests(SnapshotSummary s) {
        return s.testsRun() + " run, " + s.testFailures() + " failed";
    }

    private static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
