package com.company.autoremediate.model;

import java.util.List;

/**
 * Final disposition of one finding.
 * status: FIXED, FAILED_TO_REMEDIATE, SKIPPED_NOT_AUTOMATABLE, SKIPPED_NOT_SELECTED,
 * SKIPPED_ALREADY_RESOLVED, SKIPPED_LLM_UNSAFE.
 */
public record IssueOutcome(
        Finding finding,
        Confidence confidence,
        String classificationReason,
        String status,
        String detail,
        List<AttemptRecord> attempts,
        String commit,
        List<String> changedFiles) {}
