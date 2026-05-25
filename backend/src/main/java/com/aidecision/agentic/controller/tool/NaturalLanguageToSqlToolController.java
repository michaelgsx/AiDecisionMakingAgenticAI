package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/tools/natural_language_to_sql/{version}")
public class NaturalLanguageToSqlToolController extends AbstractToolController {

    public NaturalLanguageToSqlToolController(ToolOperationsService operations) {
        super(operations);
    }

    @Override
    protected String toolName() {
        return "natural_language_to_sql";
    }
}
