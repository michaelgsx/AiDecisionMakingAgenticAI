package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.integration.RagAssessClient;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Calls AiDecisionMakingBackend {@code POST /rag/assess}. */
@Component
public class AiDecisionRagSimilarityTool implements AgentTool {

    private final RagAssessClient rag;
    private final ObjectMapper mapper;

    public AiDecisionRagSimilarityTool(RagAssessClient rag, ObjectMapper mapper) {
        this.rag = rag;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "ai_decision_rag";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        String text = params.containsKey("text")
                ? params.get("text").toString()
                : params.getOrDefault("query", ctx.question()).toString();
        String metadata = params.getOrDefault("metadata", "{}").toString();
        if (metadata.isBlank()) {
            metadata = extractMetadataFromPrior(ctx);
        }
        try {
            Map<String, Object> out = rag.assess(text, metadata);
            out.put("query", text);
            return ToolResult.ok(out);
        } catch (Exception e) {
            return ToolResult.fail("RAG assess failed: " + e.getMessage());
        }
    }

    private String extractMetadataFromPrior(ToolExecutionContext ctx) {
        for (String json : ctx.priorOutputsByStepKey().values()) {
            try {
                Map<?, ?> m = mapper.readValue(json, Map.class);
                Object features = m.get("features");
                if (features != null) {
                    return mapper.writeValueAsString(features);
                }
            } catch (Exception ignored) {
            }
        }
        return "{}";
    }
}
