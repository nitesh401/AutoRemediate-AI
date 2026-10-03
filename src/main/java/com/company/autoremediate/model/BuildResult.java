package com.company.autoremediate.model;

import java.util.List;

public record BuildResult(boolean success, int exitCode, String output, List<String> failedTests) {}
