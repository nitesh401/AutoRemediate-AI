package com.company.autoremediate.model;

import java.util.EnumSet;
import java.util.Set;

/** Explicit job state machine. FAILED is reachable from any non-terminal state. */
public enum JobStatus {
    CREATED, CLONING, BASELINE, SCANNING, CLASSIFYING, REMEDIATING, BUILDING, TESTING,
    RESCANNING, VERIFYING, RETRY, REPORTING, PR_CREATED, REVERTED, COMPLETED, FAILED;

    public Set<JobStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(CLONING, FAILED);
            case CLONING -> EnumSet.of(BASELINE, FAILED);
            case BASELINE -> EnumSet.of(SCANNING, FAILED);
            case SCANNING -> EnumSet.of(CLASSIFYING, FAILED);
            case CLASSIFYING -> EnumSet.of(REMEDIATING, REPORTING, FAILED);
            case REMEDIATING -> EnumSet.of(BUILDING, RETRY, FAILED);
            case BUILDING -> EnumSet.of(TESTING, VERIFYING, FAILED);
            case TESTING -> EnumSet.of(RESCANNING, VERIFYING, FAILED);
            case RESCANNING -> EnumSet.of(VERIFYING, FAILED);
            case VERIFYING -> EnumSet.of(REMEDIATING, RETRY, REPORTING, FAILED);
            case RETRY -> EnumSet.of(REMEDIATING, REVERTED, FAILED);
            case REVERTED -> EnumSet.of(REMEDIATING, REPORTING, FAILED);
            case REPORTING -> EnumSet.of(PR_CREATED, COMPLETED, FAILED);
            case PR_CREATED -> EnumSet.of(COMPLETED, FAILED);
            case COMPLETED, FAILED -> EnumSet.noneOf(JobStatus.class);
        };
    }

    public boolean canTransitionTo(JobStatus target) {
        return next().contains(target);
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED;
    }
}
