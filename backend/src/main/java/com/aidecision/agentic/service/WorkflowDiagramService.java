package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.dto.WorkflowDiagramResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.orchestrator.WorkflowDag;
import com.aidecision.agentic.orchestrator.WorkflowMermaidRenderer;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowDiagramService {

    private final ObjectMapper mapper;
    private final OrchestratorRunRepository runRepo;
    private final OrchestratorStepRepository stepRepo;

    public WorkflowDiagramService(
            ObjectMapper mapper,
            OrchestratorRunRepository runRepo,
            OrchestratorStepRepository stepRepo) {
        this.mapper = mapper;
        this.runRepo = runRepo;
        this.stepRepo = stepRepo;
    }

    public WorkflowDiagramResponse renderFromJson(String workflowJson, Map<String, String> stepStatuses) {
        if (workflowJson == null || workflowJson.isBlank()) {
            throw new IllegalArgumentException("workflowJson is required");
        }
        try {
            WorkflowDag dag = mapper.readValue(workflowJson.trim(), WorkflowDag.class);
            String mermaid = WorkflowMermaidRenderer.toMermaid(dag, stepStatuses == null ? Map.of() : stepStatuses);
            return new WorkflowDiagramResponse("mermaid", mermaid);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow JSON: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public WorkflowDiagramResponse renderForRun(UUID runId) {
        OrchestratorRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        List<OrchestratorStep> steps = stepRepo.findByRunIdOrderByCreatedAtAsc(runId);
        Map<String, String> statuses = WorkflowMermaidRenderer.statusMapFromSteps(steps.stream()
                .map(s -> new WorkflowMermaidRenderer.StepStatusSource(s.getStepKey(), s.getStatus()))
                .toList());
        return renderFromJson(run.getWorkflowJson(), statuses);
    }

    public String mermaidForRunStatus(RunStatusResponse run) {
        if (run.workflowJson() == null || run.workflowJson().isBlank()) {
            return null;
        }
        Map<String, String> statuses = WorkflowMermaidRenderer.statusMapFromSteps(
                run.steps() == null
                        ? List.of()
                        : run.steps().stream()
                                .map(s -> new WorkflowMermaidRenderer.StepStatusSource(s.stepKey(), s.status()))
                                .toList());
        return WorkflowMermaidRenderer.toMermaid(parseDag(run.workflowJson()), statuses);
    }

    private WorkflowDag parseDag(String workflowJson) {
        try {
            return mapper.readValue(workflowJson, WorkflowDag.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow JSON on run", e);
        }
    }
}
