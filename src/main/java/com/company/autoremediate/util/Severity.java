package com.company.autoremediate.util;

public final class Severity {
    private Severity() {}

    /** Higher = more severe. Handles Sonar (BLOCKER..INFO) and Snyk (critical..low) vocabularies. */
    public static int rank(String severity) {
        if (severity == null) return 0;
        return switch (severity.toLowerCase()) {
            case "blocker" -> 6;
            case "critical" -> 5;
            case "high", "major" -> 4;
            case "medium" -> 3;
            case "minor", "low" -> 2;
            case "info" -> 1;
            default -> 0;
        };
    }
}
