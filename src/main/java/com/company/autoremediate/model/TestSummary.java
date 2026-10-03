package com.company.autoremediate.model;

import java.util.List;

public record TestSummary(int run, int failures, int errors, int skipped, List<String> failedTests) {
    public static TestSummary empty() {
        return new TestSummary(0, 0, 0, 0, List.of());
    }

    public boolean passed() {
        return failures == 0 && errors == 0;
    }
}
