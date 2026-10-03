package com.company.autoremediate.tools;

import com.company.autoremediate.model.SonarIssue;
import java.util.List;

public record SonarScan(boolean ok, List<SonarIssue> issues, String error) {
    public static SonarScan ok(List<SonarIssue> issues) {
        return new SonarScan(true, issues, null);
    }

    public static SonarScan failed(String error) {
        return new SonarScan(false, List.of(), error);
    }
}
