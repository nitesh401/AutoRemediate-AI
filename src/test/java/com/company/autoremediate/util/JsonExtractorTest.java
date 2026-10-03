package com.company.autoremediate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonExtractorTest {
    @Test
    void stripsFencesAndProse() {
        assertThat(JsonExtractor.extractObject("Here you go:\n```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
    }

    @Test
    void failsWithoutJson() {
        assertThatThrownBy(() -> JsonExtractor.extractObject("no json")).isInstanceOf(IllegalArgumentException.class);
    }
}
