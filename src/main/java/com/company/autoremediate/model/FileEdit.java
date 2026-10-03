package com.company.autoremediate.model;

/** One search/replace edit. 'search' must match the original file content exactly once. */
public record FileEdit(String file, String search, String replace) {}
