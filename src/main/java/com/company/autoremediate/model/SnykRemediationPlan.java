package com.company.autoremediate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnykRemediationPlan(
        boolean safeToAutomate,
        String targetVersion,
        List<String> breakingChangeRisks,
        String reasoning) {}
