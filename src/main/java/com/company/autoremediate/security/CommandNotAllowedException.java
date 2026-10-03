package com.company.autoremediate.security;

public class CommandNotAllowedException extends SecurityException {
    public CommandNotAllowedException(String message) {
        super(message);
    }
}
