package com.company.autoremediate.util;

/** Pulls the JSON object out of an LLM reply that may include prose or markdown fences. */
public final class JsonExtractor {
    private JsonExtractor() {}

    public static String extractObject(String text) {
        if (text == null) throw new IllegalArgumentException("Empty model response");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON object found in model response");
        }
        return text.substring(start, end + 1);
    }
}
