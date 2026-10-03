package com.company.autoremediate.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.TestProps;
import com.company.autoremediate.model.FileEdit;
import com.company.autoremediate.model.FindingSource;
import com.company.autoremediate.tools.PatchStats;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemediationPolicyTest {
    private final RemediationPolicy policy = new RemediationPolicy(TestProps.defaults());

    @Test
    void editValidation() {
        assertThat(policy.validateEdits(List.of(new FileEdit("src/A.java", "a", "b")))).isNull();
        assertThat(policy.validateEdits(List.of())).isNotNull();
        assertThat(policy.validateEdits(List.of(new FileEdit("pom.xml", "a", "b")))).isNotNull();
        assertThat(policy.validateEdits(List.of(new FileEdit("../A.java", "a", "b")))).isNotNull();
        assertThat(policy.validateEdits(List.of(new FileEdit("src/A.java", "", "b")))).isNotNull();
        assertThat(policy.validateEdits(List.of(new FileEdit(".github/X.java", "a", "b")))).isNotNull();
    }

    @Test
    void patchValidation() {
        assertThat(policy.validatePatch(FindingSource.SONAR, new PatchStats(List.of("src/A.java"), 2))).isNull();
        assertThat(policy.validatePatch(FindingSource.SONAR, new PatchStats(List.of(), 0))).isNotNull();
        assertThat(policy.validatePatch(FindingSource.SONAR, new PatchStats(List.of("src/A.java"), 500))).isNotNull();
        assertThat(policy.validatePatch(FindingSource.SONAR, new PatchStats(List.of("pom.xml"), 2))).isNotNull();
        assertThat(policy.validatePatch(FindingSource.SNYK, new PatchStats(List.of("pom.xml"), 2))).isNull();
        assertThat(policy.validatePatch(FindingSource.SNYK, new PatchStats(List.of("src/A.java"), 2))).isNotNull();
        assertThat(policy.validatePatch(FindingSource.SONAR,
                new PatchStats(List.of("A.java", "B.java", "C.java"), 3))).isNotNull();
    }
}
