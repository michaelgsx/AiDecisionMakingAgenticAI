package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/tools/human_in_the_loop")
public class HumanInTheLoopToolController extends AbstractToolController {

    public HumanInTheLoopToolController(ToolOperationsService operations) {
        super(operations);
    }

    @Override
    protected String toolName() {
        return "human_in_the_loop";
    }
}
