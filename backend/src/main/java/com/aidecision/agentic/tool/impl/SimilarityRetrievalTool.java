package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Legacy tool name; delegates to {@link AiDecisionRagSimilarityTool}. */
@Component
public class SimilarityRetrievalTool implements AgentTool {

    private final AiDecisionRagSimilarityTool ragTool;

    public SimilarityRetrievalTool(AiDecisionRagSimilarityTool ragTool) {
        this.ragTool = ragTool;
    }

    @Override
    public String name() {
        return "similarity_retrieval";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        if (!params.containsKey("query") && !params.containsKey("text")) {
            params = new java.util.HashMap<>(params);
            params.put("query", ctx.question());
        }
        return ragTool.execute(ctx, params);
    }
}
