package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/tools/data_acquisition/{version}")
public class DataAcquisitionToolController extends AbstractToolController {

    public DataAcquisitionToolController(ToolOperationsService operations) {
        super(operations);
    }

    @Override
    protected String toolName() {
        return "data_acquisition";
    }
}
