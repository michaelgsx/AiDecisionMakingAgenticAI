package com.aidecision.agentic.config;

import com.aidecision.agentic.tool.ToolRegistryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Registers built-in tools into {@code orchestrator_tool} once at application start. */
@Component
public class ToolRegistryStartup implements ApplicationRunner {

    private final ToolRegistryService toolRegistry;

    public ToolRegistryStartup(ToolRegistryService toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        toolRegistry.registerBuiltinToolsOnStartup();
    }
}
