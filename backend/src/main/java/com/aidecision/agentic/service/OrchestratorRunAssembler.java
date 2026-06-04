package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.ExecuteResponse;
import com.aidecision.agentic.dto.PendingAsyncDto;
import com.aidecision.agentic.dto.RunStatusResponse;
import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.orchestrator.AsyncToolKind;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.orchestrator.StepStatus;
import com.aidecision.agentic.tool.AsyncToolSupport;
import com.aidecision.agentic.tool.ToolRegistryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrchestratorRunAssembler {

    private final HumanInLoopService humanInLoop;
    private final ToolRegistryService toolRegistry;
    private final WorkflowDiagramService workflowDiagrams;
    private final ObjectMapper mapper;

    public OrchestratorRunAssembler(
            HumanInLoopService humanInLoop,
            ToolRegistryService toolRegistry,
            WorkflowDiagramService workflowDiagrams,
            ObjectMapper mapper) {
        this.humanInLoop = humanInLoop;
        this.toolRegistry = toolRegistry;
        this.workflowDiagrams = workflowDiagrams;
        this.mapper = mapper;
    }

    public RunStatusResponse toRunStatus(OrchestratorRun run, List<OrchestratorStep> steps) {
        List<RunStatusResponse.StepStatusDto> stepDtos = steps.stream()
                .map(this::toStepDto)
                .toList();

        String statusDetail = deriveStatusDetail(run.getStatus(), steps);

        List<PendingAsyncDto> pendingAsync = buildPendingAsync(run, steps);
        List<RunStatusResponse.HumanApprovalDto> approvals = pendingAsync.stream()
                .filter(p -> AsyncToolKind.INPUT_REQUIRED.name().equals(p.asyncKind()))
                .map(p -> new RunStatusResponse.HumanApprovalDto(
                        p.requestId(), p.stepKey(), p.prompt(), p.proposal()))
                .toList();

        String workflowJson = run.getWorkflowJson();
        String mermaid = null;
        if (workflowJson != null && !workflowJson.isBlank()) {
            RunStatusResponse forDiagram = new RunStatusResponse(
                    run.getRunId().toString(),
                    run.getStatus(),
                    statusDetail,
                    run.getQuestion(),
                    run.getAnswerText(),
                    run.getErrorMessage(),
                    workflowJson,
                    null,
                    run.getUserId(),
                    extractTransactionId(run),
                    stepDtos,
                    !pendingAsync.isEmpty(),
                    pendingAsync,
                    !approvals.isEmpty(),
                    approvals,
                    paths(run.getRunId()));
            mermaid = workflowDiagrams.mermaidForRunStatus(forDiagram);
        }

        return new RunStatusResponse(
                run.getRunId().toString(),
                run.getStatus(),
                statusDetail,
                run.getQuestion(),
                run.getAnswerText(),
                run.getErrorMessage(),
                workflowJson,
                mermaid,
                run.getUserId(),
                extractTransactionId(run),
                stepDtos,
                !pendingAsync.isEmpty(),
                pendingAsync,
                !approvals.isEmpty(),
                approvals,
                paths(run.getRunId()));
    }

    public ExecuteResponse toExecuteResponse(RunStatusResponse status) {
        boolean completed = RunStatus.COMPLETED.name().equals(status.status());
        return new ExecuteResponse(
                status.runId(),
                status.runId(),
                status.status(),
                status.statusDetail(),
                completed,
                status.waitingForAsync(),
                status.question(),
                status.answer(),
                status.error(),
                status.userId(),
                status.transactionId(),
                status.workflowJson(),
                status.workflowMermaid(),
                status.steps(),
                status.pendingAsync(),
                status.pollPath(),
                status.feedbackPath());
    }

    /**
     * Fine-grained phase for clients to display while polling. Mirrors the async-chat vocabulary
     * (planning, executing/{stepKey}/{toolName}, llm-answering, done, failed) so the frontend can
     * render the same detailed progress for both the sync (run polling) and async paths.
     */
    private static String deriveStatusDetail(String status, List<OrchestratorStep> steps) {
        if (RunStatus.COMPLETED.name().equals(status)) {
            return "done";
        }
        if (RunStatus.FAILED.name().equals(status)) {
            return "failed";
        }
        if (RunStatus.CANCELLED.name().equals(status)) {
            return "cancelled";
        }
        if (RunStatus.PENDING.name().equals(status)) {
            return "queued";
        }
        if (RunStatus.PLANNING.name().equals(status)) {
            return "planning";
        }
        OrchestratorStep running = steps.stream()
                .filter(s -> StepStatus.RUNNING.name().equals(s.getStatus()))
                .findFirst()
                .orElse(null);
        if (running != null) {
            if ("llm_answer".equals(running.getToolName())) {
                return "llm-answering";
            }
            return "executing/" + running.getStepKey() + "/" + running.getToolName();
        }
        if (RunStatus.WAITING_ASYNC.name().equals(status)) {
            return "waiting";
        }
        boolean anyCompleted = steps.stream()
                .anyMatch(s -> StepStatus.COMPLETED.name().equals(s.getStatus()));
        return anyCompleted ? "executing" : "planning";
    }

    private RunStatusResponse.RunPaths paths(UUID runId) {
        String base = "/agent/runs/" + runId;
        return new RunStatusResponse.RunPaths(base, base + "/feedback", base + "/poll");
    }

    private List<PendingAsyncDto> buildPendingAsync(OrchestratorRun run, List<OrchestratorStep> steps) {
        UUID runId = run.getRunId();
        String runIdStr = runId.toString();
        String feedbackPath = "/agent/runs/" + runId + "/feedback";
        String pollPath = "/agent/runs/" + runId + "/poll";
        List<PendingAsyncDto> out = new ArrayList<>();

        for (OrchestratorHumanRequest req : humanInLoop.pendingForRun(runId)) {
            OrchestratorStep step = findStep(steps, req.getStepKey(), req.getStepId());
            String toolName = step != null ? step.getToolName() : "human_in_the_loop";
            out.add(new PendingAsyncDto(
                    req.getRequestId().toString(),
                    runIdStr,
                    runIdStr,
                    run.getUserId(),
                    req.getStepKey(),
                    step != null ? step.getStepId().toString() : null,
                    toolName,
                    toolVersion(toolName),
                    AsyncToolKind.INPUT_REQUIRED.name(),
                    req.getPrompt(),
                    req.getProposal(),
                    AsyncToolSupport.allowedDecisions(AsyncToolKind.INPUT_REQUIRED),
                    feedbackPath,
                    null));
        }

        for (OrchestratorStep step : steps) {
            if (!StepStatus.RUNNING.name().equals(step.getStatus())) {
                continue;
            }
            AsyncToolKind kind = AsyncToolSupport.kind(step.getToolName());
            if (kind != AsyncToolKind.POLL_ONLY) {
                continue;
            }
            if (out.stream().anyMatch(p -> step.getStepKey().equals(p.stepKey()))) {
                continue;
            }
            String requestId = extractRequestIdFromOutput(step);
            out.add(new PendingAsyncDto(
                    requestId,
                    runIdStr,
                    runIdStr,
                    run.getUserId(),
                    step.getStepKey(),
                    step.getStepId().toString(),
                    step.getToolName(),
                    toolVersion(step.getToolName()),
                    AsyncToolKind.POLL_ONLY.name(),
                    null,
                    null,
                    List.of(),
                    null,
                    pollPath));
        }

        return out;
    }

    private String toolVersion(String toolName) {
        OrchestratorTool row = toolRegistry.enabledToolsByName().get(toolName);
        return row != null ? row.getVersion() : "1.1.0";
    }

    private static final int MAX_STEP_OUTPUT_CHARS = 32_000;

    private RunStatusResponse.StepStatusDto toStepDto(OrchestratorStep s) {
        OrchestratorTool tool = toolRegistry.enabledToolsByName().get(s.getToolName());
        return new RunStatusResponse.StepStatusDto(
                s.getStepId().toString(),
                s.getStepKey(),
                s.getToolName(),
                tool != null ? tool.getVersion() : null,
                s.getStatus(),
                s.getErrorMessage(),
                s.getAttemptCount(),
                truncateStepOutput(s.getOutputJson()));
    }

    private static String truncateStepOutput(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) {
            return null;
        }
        if (outputJson.length() <= MAX_STEP_OUTPUT_CHARS) {
            return outputJson;
        }
        return outputJson.substring(0, MAX_STEP_OUTPUT_CHARS) + "\n... [truncated]";
    }

    private static OrchestratorStep findStep(List<OrchestratorStep> steps, String stepKey, UUID stepId) {
        for (OrchestratorStep s : steps) {
            if (stepKey != null && stepKey.equals(s.getStepKey())) {
                return s;
            }
            if (stepId != null && stepId.equals(s.getStepId())) {
                return s;
            }
        }
        return null;
    }

    private String extractRequestIdFromOutput(OrchestratorStep step) {
        try {
            if (step.getOutputJson() == null || step.getOutputJson().isBlank()) {
                return step.getStepId().toString();
            }
            Map<String, Object> out = mapper.readValue(step.getOutputJson(), new TypeReference<>() {});
            Object id = out.get("requestId");
            return id != null ? id.toString() : step.getStepId().toString();
        } catch (Exception e) {
            return step.getStepId().toString();
        }
    }

    private String extractTransactionId(OrchestratorRun run) {
        try {
            if (run.getCheckpointJson() == null || run.getCheckpointJson().isBlank()) {
                return null;
            }
            Map<String, Object> cp = mapper.readValue(run.getCheckpointJson(), new TypeReference<>() {});
            Object tx = cp.get("transactionId");
            return tx != null ? tx.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
