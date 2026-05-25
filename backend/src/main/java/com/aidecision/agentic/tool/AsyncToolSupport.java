package com.aidecision.agentic.tool;

import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.orchestrator.AsyncToolKind;

import java.util.List;
import java.util.Map;

public final class AsyncToolSupport {

    private static final Map<String, AsyncToolKind> KIND_BY_TOOL = Map.of(
            "human_in_the_loop", AsyncToolKind.INPUT_REQUIRED
            // Register POLL_ONLY tools here when added, e.g. "long_running_report", POLL_ONLY
    );

    private AsyncToolSupport() {}

    public static boolean isAsync(OrchestratorTool row) {
        if (row == null) {
            return false;
        }
        if ("ASYNC".equalsIgnoreCase(row.getExecutionMode())) {
            return true;
        }
        return KIND_BY_TOOL.containsKey(row.getToolName());
    }

    public static AsyncToolKind kind(String toolName) {
        if (toolName == null) {
            return null;
        }
        return KIND_BY_TOOL.get(toolName);
    }

    public static AsyncToolKind requireAsyncKind(String toolName) {
        AsyncToolKind kind = kind(toolName);
        if (kind == null) {
            throw new IllegalArgumentException("Tool is not a registered async tool: " + toolName);
        }
        return kind;
    }

    public static List<String> allowedDecisions(AsyncToolKind kind) {
        return switch (kind) {
            case INPUT_REQUIRED -> List.of("accept", "reject");
            case POLL_ONLY -> List.of();
        };
    }
}
