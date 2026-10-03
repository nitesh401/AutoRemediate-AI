package com.company.autoremediate.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStatusTest {
    @Test
    void happyPathIsAllowed() {
        assertThat(JobStatus.CREATED.canTransitionTo(JobStatus.CLONING)).isTrue();
        assertThat(JobStatus.VERIFYING.canTransitionTo(JobStatus.RETRY)).isTrue();
        assertThat(JobStatus.RETRY.canTransitionTo(JobStatus.REVERTED)).isTrue();
        assertThat(JobStatus.REPORTING.canTransitionTo(JobStatus.PR_CREATED)).isTrue();
    }

    @Test
    void shortcutsAreForbidden() {
        assertThat(JobStatus.BASELINE.canTransitionTo(JobStatus.REPORTING)).isFalse();
        assertThat(JobStatus.REMEDIATING.canTransitionTo(JobStatus.PR_CREATED)).isFalse();
        assertThat(JobStatus.COMPLETED.next()).isEmpty();
        assertThat(JobStatus.FAILED.terminal()).isTrue();
    }
}
