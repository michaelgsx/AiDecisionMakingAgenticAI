package com.aidecision.agentic.tool;

import com.aidecision.agentic.dto.ToolSchemaDto;
import com.aidecision.agentic.dto.ToolSchemaFieldDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSchemaConverterTest {

    private final ToolSchemaConverter converter = new ToolSchemaConverter(new ObjectMapper());

    @Test
    void toJsonSchema_includesPropertyDescriptions() {
        ToolSchemaDto schema = new ToolSchemaDto(
                "User lookup inputs",
                List.of(
                        new ToolSchemaFieldDto("question", "string", "NL question for SQL generation"),
                        new ToolSchemaFieldDto("userId", "string", "Target user id filter")));

        String json = converter.toJsonSchema(schema);

        assertThat(json).contains("\"question\"");
        assertThat(json).contains("NL question for SQL generation");
        assertThat(json).contains("\"userId\"");
    }

    @Test
    void roundTrip_preservesFields() {
        ToolSchemaDto original = new ToolSchemaDto(
                "Response bundle",
                List.of(new ToolSchemaFieldDto("rowCount", "integer", "Rows returned")));

        ToolSchemaDto parsed = converter.fromJsonSchema(converter.toJsonSchema(original));

        assertThat(parsed.description()).isEqualTo("Response bundle");
        assertThat(parsed.fields()).hasSize(1);
        assertThat(parsed.fields().get(0).name()).isEqualTo("rowCount");
        assertThat(parsed.fields().get(0).type()).isEqualTo("integer");
    }
}
