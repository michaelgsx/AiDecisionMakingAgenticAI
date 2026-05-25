package com.aidecision.agentic.service;

import com.aidecision.agentic.dto.FeedbackRequest;
import com.aidecision.agentic.dto.FeedbackResponse;
import com.aidecision.agentic.entity.OrchestratorRun;
import com.aidecision.agentic.entity.QaFeedback;
import com.aidecision.agentic.repository.OrchestratorRunRepository;
import com.aidecision.agentic.repository.QaFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QaFeedbackService {

    private final QaFeedbackRepository feedback;
    private final OrchestratorRunRepository runs;

    public QaFeedbackService(QaFeedbackRepository feedback, OrchestratorRunRepository runs) {
        this.feedback = feedback;
        this.runs = runs;
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
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            row.setConversationId(UUID.fromString(request.conversationId().trim()));
        } else {
            row.setConversationId(run.getConversationId() != null ? run.getConversationId() : runId);
        }
        row.setRating(rating);
        row.setComment(blankToNull(request.comment()));
        feedback.save(row);

        return new FeedbackResponse(true, row.getFeedbackId().toString(), "Feedback recorded");
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
