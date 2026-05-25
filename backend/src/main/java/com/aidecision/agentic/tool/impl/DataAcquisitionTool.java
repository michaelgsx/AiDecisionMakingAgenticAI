package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.service.DataAcquisitionService;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DataAcquisitionTool implements AgentTool {

    private final DataAcquisitionService acquisition;

    public DataAcquisitionTool(DataAcquisitionService acquisition) {
        this.acquisition = acquisition;
    }

    @Override
    public String name() {
        return "data_acquisition";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        String scenario = params.getOrDefault("scenario", "qa").toString();
        String question = params.containsKey("question")
                ? params.get("question").toString()
                : ctx.question();
        if (question == null || question.isBlank()) {
            return ToolResult.fail("question is required (params.question or workflow context)");
        }

        int maxRows = 50;
        if (params.get("maxRows") instanceof Number n) {
            maxRows = n.intValue();
        }

        List<String> tableNames = parseTableNames(params.get("tableNames"));

        try {
            DataAcquisitionService.AcquisitionResult result =
                    acquisition.acquire(question, scenario, maxRows, tableNames);
            return ToolResult.ok(Map.of(
                    "scenario", result.scenario(),
                    "tables", result.tables(),
                    "sql", result.sql(),
                    "rows", result.rows(),
                    "rowCount", result.rowCount(),
                    "features", result.features(),
                    "note", result.note()));
        } catch (Exception e) {
            return ToolResult.fail("Data acquisition failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseTableNames(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    names.add(item.toString().trim());
                }
            }
            return names;
        }
        return List.of(raw.toString().trim());
    }
}
