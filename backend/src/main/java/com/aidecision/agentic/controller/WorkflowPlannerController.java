package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.PlannerPromptRequest;
import com.aidecision.agentic.dto.PlannerPromptResponse;
import com.aidecision.agentic.orchestrator.PlannerPrompt;
import com.aidecision.agentic.orchestrator.WorkflowPlannerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inspect the workflow planner prompt (system + user + output JSON schema) without calling Azure OpenAI.
 */
@RestController
@RequestMapping("/agent/planner")
public class WorkflowPlannerController {

    private final WorkflowPlannerService planner;

    public WorkflowPlannerController(WorkflowPlannerService planner) {
        this.planner = planner;
    }

    @PostMapping("/prompt")
    public PlannerPromptResponse buildPrompt(@Valid @RequestBody PlannerPromptRequest request) throws Exception {
        PlannerPrompt prompt = planner.buildPrompt(request.question());
        return new PlannerPromptResponse(
                prompt.systemPrompt(),
                prompt.userPrompt(),
                prompt.outputJsonSchema());
    }
}
