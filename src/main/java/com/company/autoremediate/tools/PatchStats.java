package com.company.autoremediate.tools;

import java.util.List;

public record PatchStats(List<String> files, int changedLines) {}
