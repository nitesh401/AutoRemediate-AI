package com.company.autoremediate.model;

/** A normalized scanner finding. */
public sealed interface Finding permits SonarIssue, SnykIssue {
    String id();

    FindingSource source();

    /** Stable identity used to compare findings before/after a change (line numbers are ignored). */
    String fingerprint();

    String severity();

    /** One-line human readable description (safe to log). */
    String describe();
}
