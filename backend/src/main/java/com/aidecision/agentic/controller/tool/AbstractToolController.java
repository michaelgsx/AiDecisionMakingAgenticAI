package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.dto.ToolCancelRequest;
import com.aidecision.agentic.dto.ToolCancelResponse;
import com.aidecision.agentic.dto.ToolExecuteRequest;
import com.aidecision.agentic.dto.ToolPollRequest;
import com.aidecision.agentic.dto.ToolRegistryInfoResponse;
import com.aidecision.agentic.dto.ToolRunResponse;
import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Shared REST surface for each built-in tool controller:
 * registry-info, execute, poll, cancel.
 */
public abstract class AbstractToolController {

    private final ToolOperationsService operations;

    protected AbstractToolController(ToolOperationsService operations) {
        this.operations = operations;
    }

    protected abstract String toolName();

    @GetMapping("/registry-info")
    public ToolRegistryInfoResponse registryInfo() {
        return operations.registryInfo(toolName());
    }

    @PostMapping("/execute")
    public ToolRunResponse execute(@RequestBody(required = false) ToolExecuteRequest request) {
        return operations.execute(toolName(), request != null ? request : emptyExecute());
    }

    @PostMapping("/poll")
    public ToolRunResponse poll(@RequestBody ToolPollRequest request) {
        return operations.poll(toolName(), request);
    }

    @PostMapping("/cancel")
    public ToolCancelResponse cancel(@RequestBody(required = false) ToolCancelRequest request) {
        return operations.cancel(
                toolName(), request != null ? request : new ToolCancelRequest(null, null, null, null));
    }

    private static ToolExecuteRequest emptyExecute() {
        return new ToolExecuteRequest(null, null, null, null, null);
    }
}
