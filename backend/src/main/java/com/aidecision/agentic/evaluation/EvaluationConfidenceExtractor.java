package com.aidecision.agentic.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads {@code confidence} from tool step output JSON, with tool-specific fallbacks when the
 * field is absent (LLM tools should emit it via response schema).
 */
public final class EvaluationConfidenceExtractor {

    public static final double DEFAULT_CONFIDENCE = 0.5;

    private EvaluationConfidenceExtractor() {}

    public static double extract(String outputJson, String toolName, ObjectMapper mapper) {
        if (outputJson == null || outputJson.isBlank()) {
            return DEFAULT_CONFIDENCE;
        }
        try {
            JsonNode root = mapper.readTree(outputJson);
            Double explicit = readConfidenceNode(root.path("confidence"));
            if (explicit != null) {
                return explicit;
            }
            Double nested = readConfidenceNode(root.path("features").path("confidence"));
            if (nested != null) {
                return nested;
            }
            return fallbackForTool(root, toolName);
        } catch (Exception e) {
            return DEFAULT_CONFIDENCE;
        }
    }

    private static Double readConfidenceNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return clamp(node.asDouble());
        }
        if (node.isTextual()) {
            try {
                return clamp(Double.parseDouble(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double fallbackForTool(JsonNode root, String toolName) {
        if (toolName == null) {
            return DEFAULT_CONFIDENCE;
        }
        return switch (toolName) {
            case "ai_decision_rag", "similarity_retrieval" -> ragConfidence(root);
            case "natural_language_to_sql" -> root.path("rowCount").asInt(0) > 0 ? 0.75 : 0.35;
            case "human_in_the_loop" -> "ANSWERED".equalsIgnoreCase(root.path("status").asText(""))
                    ? 1.0
                    : DEFAULT_CONFIDENCE;
            case "data_acquisition" -> root.path("rowCount").asInt(0) > 0 ? 0.7 : 0.4;
            default -> DEFAULT_CONFIDENCE;
        };
    }

    private static double ragConfidence(JsonNode root) {
        Double aiConf = readConfidenceNode(root.path("aiConfidence"));
        if (aiConf != null) {
            return aiConf;
        }
        JsonNode hits = root.path("hits");
        if (hits.isArray() && !hits.isEmpty()) {
            double max = 0;
            for (JsonNode hit : hits) {
                max = Math.max(max, hit.path("score").asDouble(0));
            }
            if (max > 0) {
                return clamp(max);
            }
        }
        return DEFAULT_CONFIDENCE;
    }

    public static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return DEFAULT_CONFIDENCE;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
