package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.WorkflowValidateRequest;
import com.aidecision.agentic.dto.WorkflowValidateResponse;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.orchestrator.InsufficientToolsException;
import com.aidecision.agentic.orchestrator.WorkflowDag;
import com.aidecision.agentic.orchestrator.WorkflowDagValidator;
import com.aidecision.agentic.orchestrator.WorkflowPlannerService;
import com.aidecision.agentic.orchestrator.WorkflowValidationResult;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WorkflowValidationService {

    private final WorkflowDagValidator dagValidator;
    private final WorkflowPlannerService planner;
    private final ToolRegistryService toolRegistry;
    private final ObjectMapper mapper;

    public WorkflowValidationService(
            WorkflowDagValidator dagValidator,
            WorkflowPlannerService planner,
            ToolRegistryService toolRegistry,
            ObjectMapper mapper) {
        this.dagValidator = dagValidator;
        this.planner = planner;
        this.toolRegistry = toolRegistry;
        this.mapper = mapper;
    }

    public WorkflowValidateResponse validate(WorkflowValidateRequest request) throws Exception {
        WorkflowDag dag;
        if (request.workflowJson() != null && !request.workflowJson().isBlank()) {
            dag = mapper.readValue(request.workflowJson(), WorkflowDag.class);
        } else if (request.question() != null && !request.question().isBlank()) {
            try {
                dag = planner.plan(request.question().trim());
            } catch (InsufficientToolsException e) {
                return new WorkflowValidateResponse(
                        false,
                        java.util.List.of(e.getMessage()),
                        java.util.List.of(),
                        null);
            }
        } else {
            throw new IllegalArgumentException("Provide question or workflowJson");
        }

        Map<String, OrchestratorTool> tools = toolRegistry.enabledToolsByName();
        WorkflowValidationResult result = dagValidator.validateExecutable(
                dag, tools, true, toolRegistry::hasExecutor);

        return new WorkflowValidateResponse(
                result.valid(),
                result.errors(),
                result.executionWaves(),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dag));
    }
}
