package com.company.autoremediate.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.TestProps;
import com.company.autoremediate.model.Confidence;
import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.SonarIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssueClassifierTest {
    private final IssueClassifier classifier = new IssueClassifier(TestProps.defaults());

    private static SonarIssue sonar(String rule, String type, String file, Integer line) {
        return new SonarIssue("k", rule, "MINOR", type, file, line, "msg");
    }

    private static SnykIssue snyk(String current, String fix, boolean direct) {
        return new SnykIssue("SNYK-1", "g:a", current, "high", "vuln", fix, direct, List.of("p", "g:a@" + current));
    }

    @Test
    void allowListedRuleIsHighConfidence() {
        assertThat(classifier.classifySonar(sonar("java:S1128", "CODE_SMELL", "src/main/java/A.java", 3)).confidence())
                .isEqualTo(Confidence.HIGH_CONFIDENCE);
    }

    @Test
    void securityAndSensitivePathsNeedHumans() {
        assertThat(classifier.classifySonar(sonar("java:S1128", "VULNERABILITY", "src/A.java", 3)).confidence())
                .isEqualTo(Confidence.HUMAN_REVIEW_REQUIRED);
        assertThat(classifier.classifySonar(sonar("java:S1128", "CODE_SMELL", "src/main/java/AuthService.java", 3)).confidence())
                .isEqualTo(Confidence.HUMAN_REVIEW_REQUIRED);
    }

    @Test
    void unknownAndMediumRulesAreNotAutomated() {
        assertThat(classifier.classifySonar(sonar("java:S2095", "BUG", "src/A.java", 3)).confidence())
                .isEqualTo(Confidence.MEDIUM_CONFIDENCE);
        assertThat(classifier.classifySonar(sonar("java:S9999", "BUG", "src/A.java", 3)).confidence())
                .isEqualTo(Confidence.LOW_CONFIDENCE);
    }

    @Test
    void snykClassification() {
        assertThat(classifier.classifySnyk(snyk("1.9", "1.10.0", true)).confidence()).isEqualTo(Confidence.HIGH_CONFIDENCE);
        assertThat(classifier.classifySnyk(snyk("1.9", "2.0.0", true)).confidence()).isEqualTo(Confidence.MEDIUM_CONFIDENCE);
        assertThat(classifier.classifySnyk(snyk("1.9", null, true)).confidence()).isEqualTo(Confidence.LOW_CONFIDENCE);
        assertThat(classifier.classifySnyk(snyk("1.9", "1.10.0", false)).confidence())
                .isEqualTo(Confidence.HUMAN_REVIEW_REQUIRED);
    }

    @Test
    void policySelectsOnlyHighConfidenceBySeverity() {
        var policy = new RemediationPolicy(TestProps.defaults());
        var all = classifier.classify(List.of(
                sonar("java:S1128", "CODE_SMELL", "src/A.java", 1),
                sonar("java:S2095", "BUG", "src/B.java", 2)), List.of(snyk("1.9", "1.10.0", true)));
        var selected = policy.select(all, 5);
        assertThat(selected).hasSize(2);
        assertThat(selected).allMatch(c -> c.confidence() == Confidence.HIGH_CONFIDENCE);
        assertThat(policy.select(all, 1)).hasSize(1);
    }
}
