package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DataAcquisitionTool implements AgentTool {

    @Override
    public String name() {
        return "data_acquisition";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        return ToolResult.ok(Map.of(
                "features", Map.of(
                        "scenario", params.getOrDefault("scenario", "orchestrator_query"),
                        "source", "demo"
                ),
                "note", "Acquired contextual risk metadata (demo)."
        ));
    }
}
