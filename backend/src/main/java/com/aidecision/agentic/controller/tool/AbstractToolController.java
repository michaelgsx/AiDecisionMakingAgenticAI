package com.aidecision.agentic.controller.tool;

import com.aidecision.agentic.dto.ToolCancelRequest;
import com.aidecision.agentic.dto.ToolCancelResponse;
import com.aidecision.agentic.dto.ToolExecuteRequest;
import com.aidecision.agentic.dto.ToolPollRequest;
import com.aidecision.agentic.dto.ToolRegistryInfoResponse;
import com.aidecision.agentic.dto.ToolRunResponse;
import com.aidecision.agentic.service.ToolOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ToolRegistryInfoResponse registryInfo(@PathVariable String version) {
        return operations.registryInfo(toolName(), version);
    }

    @PostMapping("/execute")
    public ToolRunResponse execute(
            @PathVariable String version, @RequestBody(required = false) ToolExecuteRequest request) {
        return operations.execute(toolName(), version, request != null ? request : emptyExecute());
    }

    @PostMapping("/poll")
    public ToolRunResponse poll(@PathVariable String version, @RequestBody ToolPollRequest request) {
        return operations.poll(toolName(), version, request);
    }

    @PostMapping("/cancel")
    public ToolCancelResponse cancel(
            @PathVariable String version, @RequestBody(required = false) ToolCancelRequest request) {
        return operations.cancel(
                toolName(), version, request != null ? request : new ToolCancelRequest(null, null, null, null));
    }

    private static ToolExecuteRequest emptyExecute() {
        return new ToolExecuteRequest(null, null, null, null, null);
    }
}
