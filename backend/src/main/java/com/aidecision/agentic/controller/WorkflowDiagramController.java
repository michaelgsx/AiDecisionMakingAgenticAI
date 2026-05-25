package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.WorkflowDiagramRequest;
import com.aidecision.agentic.dto.WorkflowDiagramResponse;
import com.aidecision.agentic.service.WorkflowDiagramService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/agent")
public class WorkflowDiagramController {

    private final WorkflowDiagramService diagrams;

    public WorkflowDiagramController(WorkflowDiagramService diagrams) {
        this.diagrams = diagrams;
    }

    /** Render Mermaid from workflow JSON (optionally overlay step statuses by step id). */
    @PostMapping("/workflow/diagram")
    public WorkflowDiagramResponse fromJson(@RequestBody WorkflowDiagramRequest body) {
        return diagrams.renderFromJson(body.workflowJson(), body.stepStatuses());
    }

    @GetMapping("/runs/{runId}/workflow-diagram")
    public WorkflowDiagramResponse forRun(@PathVariable String runId) {
        return diagrams.renderForRun(UUID.fromString(runId));
    }
}
