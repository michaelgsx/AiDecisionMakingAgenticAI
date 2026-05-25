package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.FeedbackRequest;
import com.aidecision.agentic.dto.FeedbackResponse;
import com.aidecision.agentic.service.QaFeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class QaFeedbackController {

    private final QaFeedbackService feedbackService;

    public QaFeedbackController(QaFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public FeedbackResponse feedback(@Valid @RequestBody FeedbackRequest request) {
        return feedbackService.submit(request);
    }
}
