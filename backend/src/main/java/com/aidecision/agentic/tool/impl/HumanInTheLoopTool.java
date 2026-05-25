package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.entity.OrchestratorHumanRequest;
import com.aidecision.agentic.entity.OrchestratorStep;
import com.aidecision.agentic.repository.OrchestratorStepRepository;
import com.aidecision.agentic.service.HumanInLoopService;
import com.aidecision.agentic.tool.AsyncAgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HumanInTheLoopTool implements AsyncAgentTool {

    private final HumanInLoopService humanService;
    private final OrchestratorStepRepository stepRepo;

    public HumanInTheLoopTool(
            HumanInLoopService humanService,
            OrchestratorStepRepository stepRepo) {
        this.humanService = humanService;
        this.stepRepo = stepRepo;
    }

    @Override
    public String name() {
        return "human_in_the_loop";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        String prompt = params.getOrDefault("prompt",
                "Is this proposed solution acceptable for the risk case?").toString();
        String proposal = params.containsKey("proposal")
                ? params.get("proposal").toString()
                : buildProposalFromPrior(ctx);

        OrchestratorStep step = stepRepo.findByRunIdOrderByCreatedAtAsc(ctx.runId()).stream()
                .filter(s -> ctx.stepKey().equals(s.getStepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Step not found: " + ctx.stepKey()));

        OrchestratorHumanRequest req = humanService.createRequest(
                ctx.runId(), step.getStepId(), ctx.stepKey(), prompt, proposal);

        return ToolResult.pending(Map.of(
                "requestId", req.getRequestId().toString(),
                "stepKey", ctx.stepKey(),
                "prompt", prompt,
                "proposal", proposal,
                "status", HumanInLoopService.STATUS_WAITING
        ));
    }

    @Override
    public ToolResult poll(ToolExecutionContext ctx, Map<String, Object> priorOutput) {
        OrchestratorStep step = stepRepo.findByRunIdOrderByCreatedAtAsc(ctx.runId()).stream()
                .filter(s -> ctx.stepKey().equals(s.getStepKey()))
                .findFirst()
                .orElse(null);
        if (step == null) {
            return ToolResult.fail("Step missing");
        }

        OrchestratorHumanRequest current = humanService.findByStepId(step.getStepId());
        if (current == null) {
            return ToolResult.fail("Human request missing");
        }
        if (HumanInLoopService.STATUS_WAITING.equals(current.getStatus())) {
            return ToolResult.pending(priorOutput);
        }

        boolean accepted = "accept".equals(current.getDecision());
        Map<String, Object> out = new HashMap<>(priorOutput);
        out.put("status", HumanInLoopService.STATUS_ANSWERED);
        out.put("decision", current.getDecision());
        out.put("accepted", accepted);
        out.put("comment", current.getComment() == null ? "" : current.getComment());
        out.put("summary", accepted ? "User accepted the proposal." : "User rejected the proposal.");
        return ToolResult.ok(out);
    }

    private String buildProposalFromPrior(ToolExecutionContext ctx) {
        if (ctx.priorOutputsByStepKey().isEmpty()) {
            return "Question: " + ctx.question();
        }
        return ctx.priorOutputsByStepKey().entrySet().stream()
                .map(e -> "[" + e.getKey() + "]\n" + e.getValue())
                .collect(Collectors.joining("\n\n"));
    }
}
