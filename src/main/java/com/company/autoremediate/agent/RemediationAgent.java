package com.company.autoremediate.agent;

import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.SnykRemediationPlan;
import com.company.autoremediate.model.SonarIssue;
import com.company.autoremediate.model.SonarRemediationPlan;
import com.company.autoremediate.tools.ContextRetriever.SourceContext;
import com.company.autoremediate.util.JsonExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Asks the LLM for remediation plans. Output is parsed into typed plans and validated by tools. */
@Component
public class RemediationAgent {
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final ObjectMapper mapper;

    public RemediationAgent(LlmClient llm, PromptTemplates prompts, ObjectMapper mapper) {
        this.llm = llm;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    public SonarRemediationPlan planSonar(SonarIssue issue, SourceContext ctx, String previousFailure) {
        Map<String, String> vars = new HashMap<>();
        vars.put("rule", issue.rule());
        vars.put("severity", issue.severity());
        vars.put("type", issue.type());
        vars.put("file", ctx.resolvedFile());
        vars.put("line", String.valueOf(issue.line()));
        vars.put("message", issue.message());
        vars.put("repositoryContext", ctx.repositoryContext());
        vars.put("source", ctx.source());
        vars.put("lineText", ctx.lineText());
        vars.put("tests", ctx.tests());
        vars.put("previousFailure", previousFailure == null ? "" : previousFailure);
        return call(PromptTemplates.SONAR, vars, SonarRemediationPlan.class);
    }

    public SnykRemediationPlan planSnyk(SnykIssue issue, String pomContent, String previousFailure) {
        Map<String, String> vars = new HashMap<>();
        vars.put("package", issue.packageName());
        vars.put("currentVersion", issue.currentVersion());
        vars.put("severity", issue.severity());
        vars.put("vulnerability", issue.vulnerability());
        vars.put("recommendedVersion", issue.recommendedVersion());
        vars.put("dependencyPath", String.join(" > ", issue.dependencyPath()));
        vars.put("pom", pomContent);
        vars.put("previousFailure", previousFailure == null ? "" : previousFailure);
        return call(PromptTemplates.SNYK, vars, SnykRemediationPlan.class);
    }

    private <T> T call(String template, Map<String, String> vars, Class<T> type) {
        String system = prompts.load(PromptTemplates.SYSTEM);
        String raw = llm.complete(system, prompts.render(template, vars));
        try {
            return mapper.readValue(JsonExtractor.extractObject(raw), type);
        } catch (Exception e) {
            throw new LlmException("Model returned an unparseable plan: " + e.getMessage(), e);
        }
    }
}
