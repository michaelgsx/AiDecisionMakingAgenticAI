package com.aidecision.agentic.controller;

import com.aidecision.agentic.config.QaUiProperties;
import com.aidecision.agentic.dto.SampleQuestionsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class QaConfigController {

    private final QaUiProperties qaUi;

    public QaConfigController(QaUiProperties qaUi) {
        this.qaUi = qaUi;
    }

    @GetMapping("/sample-questions")
    public SampleQuestionsResponse sampleQuestions() {
        List<String> questions = qaUi.getSampleQuestions().stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        return new SampleQuestionsResponse(questions);
    }
}
