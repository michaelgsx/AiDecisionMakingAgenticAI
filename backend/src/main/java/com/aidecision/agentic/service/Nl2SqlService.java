package com.aidecision.agentic.service;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Nl2SqlService {

    private static final Pattern SQL_FENCE = Pattern.compile("```(?:sql)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final AzureOpenAiProperties openAi;
    private final SchemaCatalogService catalog;
    private final ObjectMapper mapper;
    private final RestClient http;

    public Nl2SqlService(
            AzureOpenAiProperties openAi,
            SchemaCatalogService catalog,
            ObjectMapper mapper,
            RestClient http) {
        this.openAi = openAi;
        this.catalog = catalog;
        this.mapper = mapper;
        this.http = http;
    }

    public String generateSql(String question) throws Exception {
        if (!openAi.chatConfigured()) {
            return "SELECT TOP 10 table_name, description FROM dbo.schema_catalog_table";
        }

        String catalogText = catalog.buildLlmCatalogText();
        String system = """
                You write Microsoft SQL Server SELECT-only queries for risk analytics.
                Use ONLY tables/columns from the schema catalog below.
                Output ONLY the SQL (no markdown fences, no explanation).
                Rules: single SELECT; no semicolons; TOP 100 or less; read-only.
                """;

        String user = "Schema catalog:\n" + catalogText + "\n\nQuestion:\n" + question;

        String base = openAi.getEndpoint().replaceAll("/+$", "");
        URI uri = URI.create(base + "/openai/deployments/" + openAi.getChatDeployment()
                + "/chat/completions?api-version=" + openAi.getEffectiveChatApiVersion());

        ObjectNode root = mapper.createObjectNode();
        root.put("temperature", 0.1);
        root.put("max_tokens", 1024);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        String body = http.post()
                .uri(uri)
                .header("api-key", openAi.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(root.toString())
                .retrieve()
                .body(String.class);

        JsonNode json = mapper.readTree(body == null ? "{}" : body);
        String raw = json.path("choices").path(0).path("message").path("content").asText("").trim();
        return extractSql(raw);
    }

    private static String extractSql(String raw) {
        Matcher m = SQL_FENCE.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return raw.replaceAll("^```(?:sql)?\\s*", "").replaceAll("```\\s*$", "").trim();
    }
}
