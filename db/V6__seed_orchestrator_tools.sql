-- Upsert new orchestrator tools (safe on existing DBs)

MERGE dbo.orchestrator_tool AS t
USING (VALUES
    (N'ai_decision_rag', N'1.0.0',
     N'AiDecisionMakingBackend hybrid RAG assess (/rag/assess) for similar cases.',
     N'SIMILARITY_RETRIEVAL', N'SYNC',
     N'{"type":"object","properties":{"text":{"type":"string"},"metadata":{"type":"string"}}}',
     N'{"type":"object","properties":{"hits":{"type":"array"},"aiLabel":{"type":"string"},"summary":{"type":"string"}}}'),
    (N'natural_language_to_sql', N'1.0.0',
     N'Generate read-only SQL from natural language using schema_catalog_* descriptions.',
     N'AGGREGATE', N'SYNC',
     N'{"type":"object","properties":{"question":{"type":"string"},"maxRows":{"type":"integer"}}}',
     N'{"type":"object","properties":{"sql":{"type":"string"},"rows":{"type":"array"},"rowCount":{"type":"integer"}}}'),
    (N'human_in_the_loop', N'1.0.0',
     N'Async approval: ask the user whether the proposed solution is acceptable.',
     N'VALIDATION', N'ASYNC',
     N'{"type":"object","properties":{"proposal":{"type":"string"},"prompt":{"type":"string"}}}',
     N'{"type":"object","properties":{"decision":{"type":"string"},"accepted":{"type":"boolean"}}}')
) AS s (tool_name, version, description, tool_type, execution_mode, request_schema_json, response_schema_json)
ON t.tool_name = s.tool_name
WHEN NOT MATCHED THEN
    INSERT (tool_name, version, description, tool_type, execution_mode,
            request_schema_json, response_schema_json, enabled)
    VALUES (s.tool_name, s.version, s.description, s.tool_type, s.execution_mode,
            s.request_schema_json, s.response_schema_json, 1)
WHEN MATCHED THEN
    UPDATE SET
        description = s.description,
        tool_type = s.tool_type,
        execution_mode = s.execution_mode,
        request_schema_json = s.request_schema_json,
        response_schema_json = s.response_schema_json,
        updated_at = SYSUTCDATETIME();

UPDATE dbo.orchestrator_tool
SET description = N'Legacy alias — delegates to ai_decision_rag when configured.'
WHERE tool_name = N'similarity_retrieval';
