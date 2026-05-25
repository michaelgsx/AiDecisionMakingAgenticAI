package com.aidecision.agentic.tool;

import java.util.Map;

/** Tools that return {@link ToolResult#pending} and complete on {@link #poll}. */
public interface AsyncAgentTool extends AgentTool {

    /**
     * Called while step status is RUNNING. Return pending until external input arrives.
     * @param priorOutput parsed output map from the initial execute() call
     */
    ToolResult poll(ToolExecutionContext ctx, Map<String, Object> priorOutput);
}
