package com.aidecision.agentic.integration;

import com.aidecision.agentic.config.RagApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RagAssessClient {

    private final RagApiProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    public RagAssessClient(RagApiProperties props, ObjectMapper mapper, RestClient http) {
        this.props = props;
        this.mapper = mapper;
        this.http = http;
    }

    public Map<String, Object> assess(String text, String metadataJson) throws Exception {
        if (!props.configured()) {
            return demoResponse(text);
        }

        String url = props.getBaseUrl().replaceAll("/+$", "") + props.getAssessPath();
        Map<String, String> body = new HashMap<>();
        body.put("text", text == null ? "" : text);
        body.put("metadata", metadataJson == null ? "{}" : metadataJson);

        RestClient.RequestHeadersSpec<?> spec = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(body));

        if (!props.getOpsToken().isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getOpsToken());
        }

        String raw = spec.retrieve().body(String.class);
        JsonNode json = mapper.readTree(raw == null ? "{}" : raw);

        List<Map<String, Object>> hits = new ArrayList<>();
        for (JsonNode rec : json.path("similarRecords")) {
            String recordId = rec.path("recordId").asText(rec.path("id").asText(""));
            hits.add(Map.of(
                    "recordId", recordId,
                    "score", rec.path("score").asDouble(0),
                    "snippet", rec.path("snippet").asText(""),
                    "reviewOutcome", rec.path("reviewOutcome").asText(""),
                    "scenario", rec.path("scenario").asText("")
            ));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("risk", json.path("risk").asText(""));
        out.put("searchReason", json.path("reason").asText(""));
        out.put("aiLabel", json.path("aiLabel").asText(""));
        out.put("aiReason", json.path("aiReason").asText(""));
        out.put("hits", hits);
        out.put("summary", "AiDecision RAG assess returned " + hits.size() + " similar records.");
        out.put("source", "AiDecisionMakingBackend");
        return out;
    }

    private Map<String, Object> demoResponse(String text) {
        return Map.of(
                "risk", "high",
                "searchReason", "RAG API not configured (set APP_RAG_API_BASE_URL)",
                "hits", List.of(),
                "summary", "Demo mode — configure app.rag-api.base-url to call /rag/assess",
                "source", "demo"
        );
    }
}
