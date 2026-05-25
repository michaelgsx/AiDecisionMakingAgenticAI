-- Fix demo rows that used invalid UUID strings (collapsed to one run_id) + ensure all orchestrator tools

DELETE FROM dbo.qa_evaluation
WHERE run_id IN (
    SELECT run_id FROM dbo.orchestrator_run
    WHERE question LIKE N'%similar cases support freezing%'
      AND run_id NOT IN (
          'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00001',
          'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00002',
          'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00003'
      )
);

DELETE FROM dbo.orchestrator_step
WHERE run_id IN (
    SELECT run_id FROM dbo.orchestrator_run
    WHERE run_id NOT IN (
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00001',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00002',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00003'
    )
);

DELETE FROM dbo.orchestrator_human_request
WHERE run_id NOT IN (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00001',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00002',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00003'
);

DELETE FROM dbo.orchestrator_run
WHERE run_id NOT IN (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00001',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00002',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00003'
);

MERGE dbo.orchestrator_tool AS t
USING (VALUES
    (N'data_acquisition', N'1.0.0', N'Fetch risk context / features', N'DATA_ACQUISITION', N'SYNC',
     N'{"type":"object","properties":{"scenario":{"type":"string"}}}',
     N'{"type":"object","properties":{"features":{"type":"object"}}}'),
    (N'similarity_retrieval', N'1.0.0', N'Legacy alias — delegates to ai_decision_rag.', N'SIMILARITY_RETRIEVAL', N'SYNC',
     N'{"type":"object","properties":{"query":{"type":"string"}}}',
     N'{"type":"object","properties":{"hits":{"type":"array"}}}'),
    (N'llm_answer', N'1.0.0', N'Synthesize final answer from prior steps', N'LLM_REASONING', N'SYNC',
     N'{"type":"object","properties":{}}',
     N'{"type":"object","properties":{"answer":{"type":"string"}}}')
) AS s (tool_name, version, description, tool_type, execution_mode, request_schema_json, response_schema_json)
ON t.tool_name = s.tool_name
WHEN NOT MATCHED THEN
    INSERT (tool_name, version, description, tool_type, execution_mode,
            request_schema_json, response_schema_json, enabled)
    VALUES (s.tool_name, s.version, s.description, s.tool_type, s.execution_mode,
            s.request_schema_json, s.response_schema_json, 1);
