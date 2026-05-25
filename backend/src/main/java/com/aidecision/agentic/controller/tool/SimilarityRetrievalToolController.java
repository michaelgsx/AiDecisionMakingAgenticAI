package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/tools/similarity_retrieval/{version}")
public class SimilarityRetrievalToolController extends AbstractToolController {

    public SimilarityRetrievalToolController(ToolOperationsService operations) {
        super(operations);
    }

    @Override
    protected String toolName() {
        return "similarity_retrieval";
    }
}
