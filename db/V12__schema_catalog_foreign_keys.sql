-- Foreign-key edges for LLM JOIN hints (data_acquisition + NL2SQL)

IF OBJECT_ID(N'dbo.schema_catalog_foreign_key', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_catalog_foreign_key (
        fk_id          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        from_schema    NVARCHAR(64) NOT NULL DEFAULT N'dbo',
        from_table     NVARCHAR(128) NOT NULL,
        from_column    NVARCHAR(128) NOT NULL,
        to_schema      NVARCHAR(64) NOT NULL DEFAULT N'dbo',
        to_table       NVARCHAR(128) NOT NULL,
        to_column      NVARCHAR(128) NOT NULL,
        description    NVARCHAR(1000) NOT NULL,
        enabled        BIT NOT NULL DEFAULT 1,
        created_at     DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_schema_fk_from_table FOREIGN KEY (from_table)
            REFERENCES dbo.schema_catalog_table(table_name) ON DELETE CASCADE,
        CONSTRAINT FK_schema_fk_to_table FOREIGN KEY (to_table)
            REFERENCES dbo.schema_catalog_table(table_name)
    );
    CREATE INDEX IX_schema_fk_from ON dbo.schema_catalog_foreign_key(from_table, enabled);
    CREATE INDEX IX_schema_fk_to ON dbo.schema_catalog_foreign_key(to_table, enabled);
END;

MERGE dbo.schema_catalog_foreign_key AS t
USING (VALUES
    -- Risk / ML domain
    (N'risk_ingest_records', N'record_uuid', N'risk_features', N'request_id',
     N'Ingest row links to risk_features.request_id (same case UUID).'),
    (N'risk_decisions', N'request_id', N'risk_features', N'request_id',
     N'Audit decisions per case; join on request_id.'),
    (N'risk_embeddings', N'request_id', N'risk_features', N'request_id',
     N'Embeddings keyed by case request_id.'),
    (N'risk_feature_binned', N'request_id', N'risk_features', N'request_id',
     N'Binned features per case.'),
    (N'risk_feature_binned', N'calibration_id', N'risk_feature_bin_calibrations', N'calibration_id',
     N'Binned row belongs to a calibration definition.'),
    (N'risk_feature_bin_edges', N'calibration_id', N'risk_feature_bin_calibrations', N'calibration_id',
     N'Bin edges belong to a calibration run.'),
    (N'activity_log', N'user_id', N'risk_features', N'user_id',
     N'Logical join: activity events for the same customer as risk cases (not enforced in DB).'),

    -- Q&A chat
    (N'qa_message', N'conversation_id', N'qa_conversation', N'conversation_id',
     N'Messages belong to a conversation thread.'),
    (N'qa_feedback', N'message_id', N'qa_message', N'message_id',
     N'Thumbs feedback targets one message.'),
    (N'qa_feedback', N'conversation_id', N'qa_conversation', N'conversation_id',
     N'Feedback row also references the parent conversation.'),
    (N'qa_feedback', N'run_id', N'orchestrator_run', N'run_id',
     N'Optional link from feedback to the orchestrator run that produced the answer.'),

    -- Orchestrator workflow
    (N'orchestrator_run', N'conversation_id', N'qa_conversation', N'conversation_id',
     N'Run may be tied to an existing Q&A session.'),
    (N'orchestrator_step', N'run_id', N'orchestrator_run', N'run_id',
     N'DAG step instances for a workflow run.'),
    (N'orchestrator_step', N'tool_name', N'orchestrator_tool', N'tool_name',
     N'Step invokes a registered tool definition.'),
    (N'orchestrator_human_request', N'run_id', N'orchestrator_run', N'run_id',
     N'Human-in-the-loop approval belongs to a run.'),
    (N'orchestrator_human_request', N'step_id', N'orchestrator_step', N'step_id',
     N'Approval targets the step that requested input.'),
    (N'qa_evaluation', N'run_id', N'orchestrator_run', N'run_id',
     N'Post-run human review queue entry per orchestrator run (1:1).')
) AS s(from_table, from_column, to_table, to_column, description)
ON t.from_table = s.from_table AND t.from_column = s.from_column
   AND t.to_table = s.to_table AND t.to_column = s.to_column
WHEN MATCHED THEN
    UPDATE SET description = s.description, enabled = 1
WHEN NOT MATCHED THEN
    INSERT (from_table, from_column, to_table, to_column, description)
    VALUES (s.from_table, s.from_column, s.to_table, s.to_column, s.description);
