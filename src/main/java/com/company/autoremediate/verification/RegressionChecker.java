package com.company.autoremediate.verification;

import com.company.autoremediate.model.TestSummary;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Test-level regression rules. */
@Component
public class RegressionChecker {

    public List<String> check(TestSummary baseline, TestSummary post) {
        List<String> reasons = new ArrayList<>();
        if (post == null) {
            reasons.add("No test results available after remediation");
            return reasons;
        }
        if (!post.passed()) {
            reasons.add("Tests failing after remediation: " + (post.failures() + post.errors())
                    + (post.failedTests().isEmpty() ? "" : " " + post.failedTests()));
        }
        if (baseline != null && post.run() < baseline.run()) {
            reasons.add("Test count dropped from " + baseline.run() + " to " + post.run() + " (unexplained regression)");
        }
        return reasons;
    }
}
