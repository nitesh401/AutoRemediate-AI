package com.company.autoremediate.model;

import java.util.List;

public record AttemptRecord(int attempt, String outcome, List<String> reasons, String diff) {}
