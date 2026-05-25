package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.ToolCancelRequest;
import com.aidecision.agentic.dto.ToolCancelResponse;
import com.aidecision.agentic.dto.ToolExecuteRequest;
import com.aidecision.agentic.dto.ToolPollRequest;
import com.aidecision.agentic.dto.ToolRegistryInfoResponse;
import com.aidecision.agentic.dto.ToolRunResponse;
import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.AsyncAgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.aidecision.agentic.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ToolOperationsService {

    private final ToolRegistryService toolRegistry;
    private final HumanInLoopService humanInLoop;
    private final ObjectMapper mapper;

    public ToolOperationsService(
            ToolRegistryService toolRegistry,
            HumanInLoopService humanInLoop,
            ObjectMapper mapper) {
        this.toolRegistry = toolRegistry;
        this.humanInLoop = humanInLoop;
        this.mapper = mapper;
    }

    public ToolRegistryInfoResponse registryInfo(String toolName) {
        OrchestratorTool row = toolRegistry.enabledToolsByName().get(toolName);
        if (row == null) {
            throw new IllegalArgumentException("Unknown or disabled tool: " + toolName);
        }
        AgentTool executor = toolRegistry.requireExecutor(toolName);
        boolean async = executor instanceof AsyncAgentTool;
        return new ToolRegistryInfoResponse(
                row.getToolName(),
                row.getVersion(),
                row.getDescription(),
                row.getToolType(),
                row.getExecutionMode(),
                row.isEnabled(),
                parseSchema(row.getRequestSchemaJson()),
                parseSchema(row.getResponseSchemaJson()),
                async,
                "human_in_the_loop".equals(toolName));
    }

    public ToolRunResponse execute(String toolName, ToolExecuteRequest request) {
        AgentTool tool = toolRegistry.requireExecutor(toolName);
        ToolResult result = tool.execute(buildContext(toolName, request), safeParams(request));
        return toRunResponse(toolName, result);
    }

    public ToolRunResponse poll(String toolName, ToolPollRequest request) {
        AgentTool tool = toolRegistry.requireExecutor(toolName);
        if (!(tool instanceof AsyncAgentTool async)) {
            throw new IllegalArgumentException("Tool does not support poll (not ASYNC): " + toolName);
        }
        if (request.priorOutput() == null || request.priorOutput().isEmpty()) {
            throw new IllegalArgumentException("priorOutput is required for poll");
        }
        ToolExecutionContext ctx = buildContext(
                toolName, request.runId(), request.stepKey(), request.question(), request.priorOutputsByStepKey());
        ToolResult result = async.poll(ctx, request.priorOutput());
        return toRunResponse(toolName, result);
    }

    public ToolCancelResponse cancel(String toolName, ToolCancelRequest request) {
        if ("human_in_the_loop".equals(toolName)) {
            UUID requestId = request.requestId();
            if (requestId == null && request.runId() != null && request.stepKey() != null) {
                OrchestratorHumanRequest byStep =
                        humanInLoop.findByRunAndStepKey(request.runId(), request.stepKey());
                if (byStep != null) {
                    requestId = byStep.getRequestId();
                }
            }
            if (requestId == null) {
                throw new IllegalArgumentException("requestId or (runId + stepKey) required to cancel");
            }
            humanInLoop.cancel(requestId, request.reason());
            return new ToolCancelResponse(toolName, true, "Human request cancelled (EXPIRED)");
        }
        if (toolRegistry.requireExecutor(toolName) instanceof AsyncAgentTool) {
            return new ToolCancelResponse(toolName, false,
                    "Cancel via API is only implemented for human_in_the_loop (use requestId)");
        }
        throw new IllegalArgumentException("Tool does not support cancel: " + toolName);
    }

    private ToolExecutionContext buildContext(String toolName, ToolExecuteRequest request) {
        return buildContext(toolName, request.runId(), request.stepKey(), request.question(),
                request.priorOutputsByStepKey());
    }

    private ToolExecutionContext buildContext(
            String toolName,
            UUID runId,
            String stepKey,
            String question,
            Map<String, String> priorOutputs) {
        UUID rid = runId != null ? runId : UUID.randomUUID();
        String sk = stepKey == null || stepKey.isBlank() ? "api" : stepKey.trim();
        String q = question == null ? "" : question;
        Map<String, String> prior = priorOutputs == null ? Map.of() : priorOutputs;
        return new ToolExecutionContext(rid, sk, q, prior);
    }

    private static Map<String, Object> safeParams(ToolExecuteRequest request) {
        return request.params() == null ? Map.of() : request.params();
    }

    private ToolRunResponse toRunResponse(String toolName, ToolResult result) {
        return new ToolRunResponse(
                toolName,
                result.success(),
                result.output(),
                result.errorMessage(),
                result.asyncPending());
    }

    private Object parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
