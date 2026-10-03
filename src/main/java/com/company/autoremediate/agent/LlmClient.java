package com.company.autoremediate.agent;

/** Provider-agnostic LLM access. Application code never depends on a specific model. */
public interface LlmClient {
    boolean isConfigured();

    String complete(String systemPrompt, String userPrompt);

    String modelName();
}
