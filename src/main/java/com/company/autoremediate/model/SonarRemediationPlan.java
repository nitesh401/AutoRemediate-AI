package com.company.autoremediate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SonarRemediationPlan(
        String issueSummary,
        String rootCause,
        boolean safeToAutomate,
        String confidence,
        List<String> affectedFiles,
        List<FileEdit> edits,
        List<String> testsToRun,
        List<String> risks) {

    public List<FileEdit> editsOrEmpty() {
        return edits == null ? List.of() : edits;
    }
}
