package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.service.LlmSqlGenerationService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shrinks prior step outputs when a downstream tool hits LLM context limits. */
@Component
public class WorkflowContextSummarizer {

    private static final int HEURISTIC_MAX_CHARS = 4_000;

    private static final String SUMMARY_SYSTEM = """
            You compress workflow tool outputs for a downstream LLM step.
            Preserve facts, identifiers, counts, SQL table names, and decision labels.
            Remove redundant JSON structure and repeated rows.
            Output plain text only, under 2000 characters.
            """;

    private final LlmSqlGenerationService llm;

    public WorkflowContextSummarizer(LlmSqlGenerationService llm) {
        this.llm = llm;
    }

    public Map<String, String> summarize(Map<String, String> priorOutputsByStepKey) {
        Map<String, String> summarized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : priorOutputsByStepKey.entrySet()) {
            summarized.put(entry.getKey(), summarizeOne(entry.getKey(), entry.getValue()));
        }
        return summarized;
    }

    private String summarizeOne(String stepKey, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.length() <= HEURISTIC_MAX_CHARS && !llm.isChatConfigured()) {
            return raw;
        }
        if (!llm.isChatConfigured()) {
            return heuristicTruncate(raw);
        }
        try {
            String user = "Step key: " + stepKey + "\n\nTool output to summarize:\n" + raw;
            return llm.chatComplete(SUMMARY_SYSTEM, user, 1024, 0.0);
        } catch (Exception e) {
            return heuristicTruncate(raw);
        }
    }

    private static String heuristicTruncate(String raw) {
        if (raw.length() <= HEURISTIC_MAX_CHARS) {
            return raw;
        }
        return raw.substring(0, HEURISTIC_MAX_CHARS)
                + "\n… [truncated " + (raw.length() - HEURISTIC_MAX_CHARS) + " chars]";
    }
}
