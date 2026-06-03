package com.aidecision.agentic.tool;

import com.aidecision.agentic.dto.ToolSchemaDto;
import com.aidecision.agentic.dto.ToolSchemaFieldDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Converts structured registration schemas ↔ JSON Schema strings stored in orchestrator_tool. */
@Component
public class ToolSchemaConverter {

    private final ObjectMapper mapper;

    public ToolSchemaConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJsonSchema(ToolSchemaDto schema) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "object");
            if (schema.description() != null && !schema.description().isBlank()) {
                root.put("description", schema.description().trim());
            }
            ObjectNode properties = root.putObject("properties");
            for (ToolSchemaFieldDto field : schema.fields()) {
                ObjectNode prop = properties.putObject(field.name());
                prop.put("type", field.type().trim());
                prop.put("description", field.description().trim());
                if ("array".equalsIgnoreCase(field.type())) {
                    prop.putObject("items").put("type", "string");
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize tool schema: " + e.getMessage());
        }
    }

    public ToolSchemaDto fromJsonSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return new ToolSchemaDto(null, List.of());
        }
        try {
            JsonNode root = mapper.readTree(schemaJson);
            String description = textOrNull(root.path("description"));
            JsonNode properties = root.path("properties");
            if (!properties.isObject()) {
                return new ToolSchemaDto(description, List.of());
            }
            List<ToolSchemaFieldDto> fields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode prop = entry.getValue();
                fields.add(new ToolSchemaFieldDto(
                        entry.getKey(),
                        prop.path("type").asText("string"),
                        prop.path("description").asText("")));
            }
            return new ToolSchemaDto(description, fields);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid stored tool schema JSON: " + e.getMessage());
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText("");
        return text.isBlank() ? null : text;
    }
}
