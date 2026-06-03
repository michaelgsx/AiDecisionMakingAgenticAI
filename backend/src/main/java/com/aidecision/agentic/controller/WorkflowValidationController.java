package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.WorkflowValidateRequest;
import com.aidecision.agentic.dto.WorkflowValidateResponse;
import com.aidecision.agentic.service.WorkflowValidationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/workflow")
public class WorkflowValidationController {

    private final WorkflowValidationService validationService;

    public WorkflowValidationController(WorkflowValidationService validationService) {
        this.validationService = validationService;
    }

    /** Validate planned or supplied workflow: acyclic, known SYNC tools, parallel execution waves. */
    @PostMapping("/validate")
    public WorkflowValidateResponse validate(@RequestBody WorkflowValidateRequest request) throws Exception {
        return validationService.validate(request);
    }
}
