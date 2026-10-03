package com.company.autoremediate.verification;

import com.company.autoremediate.model.Finding;
import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.SonarIssue;
import com.company.autoremediate.model.Snapshot;
import com.company.autoremediate.model.VerificationResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The safety gate. Entirely deterministic: no LLM output can turn a FAIL into a PASS.
 *
 * SAFE only if: baseline build was green AND post build is green AND tests pass without a drop in
 * test count AND the target finding is resolved AND no new Sonar issue AND no new Snyk vulnerability.
 * If a scanner worked on the baseline, it must also work after the change (otherwise we cannot prove it).
 */
@Component
public class VerificationEngine {
    private final SonarComparator sonarComparator;
    private final SnykComparator snykComparator;
    private final RegressionChecker regressionChecker;

    public VerificationEngine(SonarComparator sonarComparator, SnykComparator snykComparator,
                              RegressionChecker regressionChecker) {
        this.sonarComparator = sonarComparator;
        this.snykComparator = snykComparator;
        this.regressionChecker = regressionChecker;
    }

    public VerificationResult verify(Snapshot baseline, Snapshot post, Finding target) {
        List<String> reasons = new ArrayList<>();
        List<SonarIssue> newSonar = List.of();
        List<SnykIssue> newSnyk = List.of();
        int sonarDelta = 0;
        int snykDelta = 0;
        boolean targetResolved = false;

        if (!baseline.buildPassed()) {
            reasons.add("BASELINE_FAILURE: baseline build did not pass, so nothing can be verified");
        }
        if (!post.buildPassed()) {
            reasons.add("Build failed after remediation");
        } else {
            reasons.addAll(regressionChecker.check(baseline.tests(), post.tests()));

            if (baseline.sonarAvailable()) {
                if (!post.sonarAvailable()) {
                    reasons.add("Sonar rescan unavailable; cannot prove the absence of new Sonar issues");
                } else {
                    Comparison<SonarIssue> c = sonarComparator.compare(baseline.sonarIssues(), post.sonarIssues());
                    newSonar = c.newItems();
                    sonarDelta = c.delta();
                    if (!newSonar.isEmpty()) reasons.add(newSonar.size() + " new Sonar issue(s) introduced");
                }
            }
            if (baseline.snykAvailable()) {
                if (!post.snykAvailable()) {
                    reasons.add("Snyk rescan unavailable; cannot prove the absence of new vulnerabilities");
                } else {
                    Comparison<SnykIssue> c = snykComparator.compare(baseline.snykIssues(), post.snykIssues());
                    newSnyk = c.newItems();
                    snykDelta = c.delta();
                    if (!newSnyk.isEmpty()) reasons.add(newSnyk.size() + " new Snyk vulnerability(ies) introduced");
                }
            }

            if (target instanceof SonarIssue s) {
                targetResolved = baseline.sonarAvailable() && post.sonarAvailable()
                        && sonarComparator.isResolved(s, baseline.sonarIssues(), post.sonarIssues());
            } else if (target instanceof SnykIssue s) {
                targetResolved = baseline.snykAvailable() && post.snykAvailable()
                        && snykComparator.isResolved(s, post.snykIssues());
            }
            if (!targetResolved) reasons.add("Target finding is not resolved: " + target.describe());
        }

        return new VerificationResult(reasons.isEmpty(), targetResolved, newSonar, newSnyk,
                sonarDelta, snykDelta, reasons);
    }
}
