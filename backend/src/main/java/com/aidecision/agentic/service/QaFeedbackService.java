package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.FeedbackRequest;
import com.aidecision.agentic.dto.FeedbackResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.QaFeedback;
import com.aidecision.agentic.orchestrator.RunStatus;
import com.aidecision.agentic.orchestrator.WorkflowPlannerService;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.QaConversationRepository;
import com.aidecision.agentic.repository.QaFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QaFeedbackService {

    private final QaFeedbackRepository feedback;
    private final OrchestratorRunRepository runs;
    private final QaConversationRepository conversations;
    private final WorkflowPlannerService planner;

    public QaFeedbackService(
            QaFeedbackRepository feedback,
            OrchestratorRunRepository runs,
            QaConversationRepository conversations,
            WorkflowPlannerService planner) {
        this.feedback = feedback;
        this.runs = runs;
        this.conversations = conversations;
        this.planner = planner;
    }

    @Transactional
    public FeedbackResponse submit(FeedbackRequest request) {
        UUID runId = UUID.fromString(request.runId().trim());
        UUID messageId = UUID.fromString(request.messageId().trim());
        String rating = request.rating().trim().toLowerCase();

        OrchestratorRun run = runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));

        QaFeedback row = feedback.findByMessageId(messageId).orElseGet(QaFeedback::new);
        row.setRunId(runId);
        row.setMessageId(messageId);
        row.setConversationId(resolveConversationId(request, run));
        row.setRating(rating);
        row.setComment(blankToNull(request.comment()));
        feedback.save(row);

        boolean workflowCached = false;
        if ("up".equals(rating)) {
            planner.cacheWorkflowFromThumbsUp(run);
            workflowCached = RunStatus.COMPLETED.name().equals(run.getStatus())
                    && run.getWorkflowJson() != null
                    && !run.getWorkflowJson().isBlank();
        }

        String message = workflowCached
                ? "Feedback recorded. Workflow saved for similar questions."
                : "Feedback recorded.";
        return new FeedbackResponse(true, row.getFeedbackId().toString(), message);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    /**
     * Orchestrator answers are keyed by run_id; conversation_id is optional and must reference
     * an existing qa_conversation row. Clients sometimes send runId as conversationId — ignore that.
     */
    private UUID resolveConversationId(FeedbackRequest request, OrchestratorRun run) {
        UUID fromRun = run.getConversationId();
        if (fromRun != null && !fromRun.equals(run.getRunId()) && conversations.existsById(fromRun)) {
            return fromRun;
        }
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            UUID requested = UUID.fromString(request.conversationId().trim());
            if (!requested.equals(run.getRunId()) && conversations.existsById(requested)) {
                return requested;
            }
        }
        return null;
    }
}
