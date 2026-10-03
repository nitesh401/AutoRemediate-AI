package com.company.autoremediate.model;

import java.util.List;

public record PatchResult(boolean applied, List<String> changedFiles, int changedLines, String diff, String error) {
    public static PatchResult failure(String error) {
        return new PatchResult(false, List.of(), 0, "", error);
    }
}
