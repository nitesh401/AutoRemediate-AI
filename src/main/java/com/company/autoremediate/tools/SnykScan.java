package com.company.autoremediate.tools;

import com.company.autoremediate.model.SnykIssue;
import java.util.List;

public record SnykScan(boolean ok, List<SnykIssue> issues, String error) {
    public static SnykScan ok(List<SnykIssue> issues) {
        return new SnykScan(true, issues, null);
    }

    public static SnykScan failed(String error) {
        return new SnykScan(false, List.of(), error);
    }
}
