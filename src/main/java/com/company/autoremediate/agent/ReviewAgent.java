package com.company.autoremediate.agent;

import com.company.autoremediate.model.ReviewDecision;
import com.company.autoremediate.util.JsonExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Second-opinion review of a diff. Fails closed: anything unclear is a REJECT. */
@Component
public class ReviewAgent {
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final ObjectMapper mapper;

    public ReviewAgent(LlmClient llm, PromptTemplates prompts, ObjectMapper mapper) {
        this.llm = llm;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    public ReviewDecision review(String issueDescription, String diff) {
        try {
            String user = prompts.render(PromptTemplates.REVIEW, Map.of(
                    "issue", issueDescription,
                    "diff", diff.length() > 30_000 ? diff.substring(0, 30_000) + "\n... [truncated]" : diff));
            String raw = llm.complete(prompts.load(PromptTemplates.SYSTEM), user);
            ReviewDecision decision = mapper.readValue(JsonExtractor.extractObject(raw), ReviewDecision.class);
            if (decision.decision() == null) return new ReviewDecision("REJECT", List.of("Review returned no decision"));
            return decision;
        } catch (Exception e) {
            return new ReviewDecision("REJECT", List.of("Review failed (fail-closed): " + e.getMessage()));
        }
    }
}
