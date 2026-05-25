package com.aidecision.agentic.tool;

import java.util.Map;

public record ToolResult(
        boolean success,
        Map<String, Object> output,
        String errorMessage,
        boolean asyncPending
) {
    public static ToolResult ok(Map<String, Object> output) {
        return new ToolResult(true, output, null, false);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, Map.of(), message, false);
    }

    public static ToolResult pending(Map<String, Object> partial) {
        return new ToolResult(true, partial, null, true);
    }
}
