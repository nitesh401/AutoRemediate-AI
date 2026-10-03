package com.company.autoremediate.verification;

import com.company.autoremediate.model.SnykIssue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Compares Snyk findings by vulnerability id + package (version excluded, since the fix changes it). */
@Component
public class SnykComparator {

    public Comparison<SnykIssue> compare(List<SnykIssue> baseline, List<SnykIssue> post) {
        Set<String> baseKeys = fingerprints(baseline);
        Set<String> postKeys = fingerprints(post);
        List<SnykIssue> added = post.stream().filter(i -> !baseKeys.contains(i.fingerprint())).toList();
        List<SnykIssue> resolved = baseline.stream().filter(i -> !postKeys.contains(i.fingerprint())).toList();
        return new Comparison<>(added, resolved, baseline.size(), post.size());
    }

    public boolean isResolved(SnykIssue target, List<SnykIssue> post) {
        return post.stream().noneMatch(i -> i.fingerprint().equals(target.fingerprint()));
    }

    private static Set<String> fingerprints(List<SnykIssue> issues) {
        Set<String> s = new HashSet<>();
        issues.forEach(i -> s.add(i.fingerprint()));
        return s;
    }
}
