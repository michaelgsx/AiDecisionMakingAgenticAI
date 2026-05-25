package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/tools/ai_decision_rag")
public class AiDecisionRagToolController extends AbstractToolController {

    public AiDecisionRagToolController(ToolOperationsService operations) {
        super(operations);
    }

    @Override
    protected String toolName() {
        return "ai_decision_rag";
    }
}
