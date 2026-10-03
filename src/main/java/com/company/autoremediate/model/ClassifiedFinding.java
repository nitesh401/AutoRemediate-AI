package com.company.autoremediate.model;

public record ClassifiedFinding(Finding finding, Confidence confidence, String reason) {}
