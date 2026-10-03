package com.company.autoremediate.agent;

import com.company.autoremediate.model.VerificationResult;
import com.company.autoremediate.util.JsonExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Explains why the deterministic gate rejected an attempt, so the next attempt can improve.
 * It never produces the PASS/FAIL decision; that is VerificationEngine's job.
 */
@Component
public class VerificationAgent {
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final ObjectMapper mapper;

    public VerificationAgent(LlmClient llm, PromptTemplates prompts, ObjectMapper mapper) {
        this.llm = llm;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    public String diagnose(String issueDescription, VerificationResult result, String buildOutput) {
        try {
            String tail = buildOutput == null ? "" : buildOutput;
            if (tail.length() > 12_000) tail = tail.substring(tail.length() - 12_000);
            String user = prompts.render(PromptTemplates.VERIFICATION, Map.of(
                    "issue", issueDescription,
                    "reasons", String.join("\n", result.reasons()),
                    "buildOutput", tail));
            String raw = llm.complete(prompts.load(PromptTemplates.SYSTEM), user);
            JsonNode node = mapper.readTree(JsonExtractor.extractObject(raw));
            return node.path("diagnosis").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
