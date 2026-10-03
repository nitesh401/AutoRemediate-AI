package com.company.autoremediate.tools;

import java.time.Duration;

public record CommandResult(int exitCode, String output, boolean timedOut, Duration duration) {
    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
