package com.company.autoremediate.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RemediationRequest(
        @NotBlank String repositoryUrl,
        String branch,
        @Min(1) @Max(50) Integer maxIssues,
        Boolean createPullRequest) {

    public boolean wantsPullRequest() {
        return Boolean.TRUE.equals(createPullRequest);
    }
}
