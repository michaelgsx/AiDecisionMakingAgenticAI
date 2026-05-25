package com.aidecision.agentic.tool;

import java.util.Map;

public interface AgentTool {

    String name();

    ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params);
}
