package com.aidecision.agentic.tool.impl;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.aidecision.agentic.evaluation.EvaluationConfidenceExtractor;
import com.aidecision.agentic.tool.AgentTool;
import com.aidecision.agentic.tool.ToolExecutionContext;
import com.aidecision.agentic.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LlmAnswerTool implements AgentTool {

    private static final String SYSTEM_PROMPT = """
            You are a risk-control Q&A assistant. Use tool outputs in context.
            Respond with JSON only: {"answer":"<complete natural-language reply>","confidence":0.0-1.0}.
            confidence is your certainty that the answer is well supported by the tool context (1.0 = fully supported).
            """;

    private final AzureOpenAiProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    public LlmAnswerTool(AzureOpenAiProperties props, ObjectMapper mapper, RestClient http) {
        this.props = props;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public String name() {
        return "llm_answer";
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx, Map<String, Object> params) {
        String prior = ctx.priorOutputsByStepKey().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));

        if (!props.chatConfigured()) {
            return ToolResult.ok(Map.of(
                    "answer",
                    "(Demo) Based on prior steps:\n" + prior + "\n\nQuestion: " + ctx.question(),
                    "confidence", 0.5));
        }

        try {
            String raw = callChat(ctx.question(), prior);
            return ToolResult.ok(parseAnswerPayload(raw));
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    private Map<String, Object> parseAnswerPayload(String raw) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("{")) {
            JsonNode node = mapper.readTree(trimmed);
            String answer = node.path("answer").asText("").trim();
            if (answer.isBlank()) {
                answer = trimmed;
            }
            double confidence = EvaluationConfidenceExtractor.clamp(node.path("confidence").asDouble(0.5));
            return Map.of("answer", answer, "confidence", confidence);
        }
        return Map.of("answer", trimmed, "confidence", 0.5);
    }

    private String callChat(String question, String context) throws Exception {
        String base = props.getEndpoint().replaceAll("/+$", "");
        URI uri = URI.create(base + "/openai/deployments/" + props.getChatDeployment()
                + "/chat/completions?api-version=" + props.getEffectiveChatApiVersion());

        ObjectNode root = mapper.createObjectNode();
        root.put("max_completion_tokens", 2048);
        root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content",
                "Question:\n" + question + "\n\nTool context:\n" + context);

        String body = http.post()
                .uri(uri)
                .header("api-key", props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(root.toString())
                .retrieve()
                .body(String.class);

        JsonNode json = mapper.readTree(body == null ? "{}" : body);
        return json.path("choices").path(0).path("message").path("content").asText("").trim();
    }
}
