package com.company.autoremediate.model;

public record SonarIssue(
        String id,
        String rule,
        String severity,
        String type,
        String file,
        Integer line,
        String message) implements Finding {

    @Override
    public FindingSource source() {
        return FindingSource.SONAR;
    }

    @Override
    public String fingerprint() {
        String normalized = message == null ? "" : message.replaceAll("\\d+", "#");
        return rule + "|" + file + "|" + normalized;
    }

    @Override
    public String describe() {
        return "Sonar " + rule + " (" + severity + ") " + file + (line != null ? ":" + line : "") + " - " + message;
    }
}
