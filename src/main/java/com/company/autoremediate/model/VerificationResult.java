package com.company.autoremediate.model;

import java.util.List;

public record VerificationResult(
        boolean safe,
        boolean targetResolved,
        List<SonarIssue> newSonarIssues,
        List<SnykIssue> newSnykIssues,
        int sonarDelta,
        int snykDelta,
        List<String> reasons) {

    public String decision() {
        return safe ? "SAFE_FOR_PR" : "REJECTED";
    }
}
