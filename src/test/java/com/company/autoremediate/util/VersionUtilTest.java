package com.company.autoremediate.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionUtilTest {
    @Test
    void comparesNumerically() {
        assertThat(VersionUtil.compare("1.10.0", "1.9.0")).isPositive();
        assertThat(VersionUtil.compare("1.9", "1.9.0")).isZero();
        assertThat(VersionUtil.isUpgrade("1.9", "1.10.0")).isTrue();
        assertThat(VersionUtil.isUpgrade("2.0.0", "1.10.0")).isFalse();
    }

    @Test
    void toleratesQualifiers() {
        assertThat(VersionUtil.parse("5.3.20.RELEASE")).containsExactly(5, 3, 20);
        assertThat(VersionUtil.parse("2.0.0-beta")).containsExactly(2, 0, 0);
        assertThat(VersionUtil.parse("garbage")).isEmpty();
    }

    @Test
    void detectsMajorChange() {
        assertThat(VersionUtil.sameMajor("1.9.0", "1.10.0")).isTrue();
        assertThat(VersionUtil.sameMajor("1.9.0", "2.0.0")).isFalse();
    }
}
