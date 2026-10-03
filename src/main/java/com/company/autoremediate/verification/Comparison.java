package com.company.autoremediate.verification;

import java.util.List;

public record Comparison<T>(List<T> newItems, List<T> resolvedItems, int baselineCount, int postCount) {
    public int delta() {
        return postCount - baselineCount;
    }
}
