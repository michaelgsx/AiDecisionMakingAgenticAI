-- AI-generated demo descriptions for NL2SQL (not production governance)

IF NOT EXISTS (SELECT 1 FROM dbo.schema_catalog_table WHERE table_name = N'risk_features')
BEGIN
    INSERT INTO dbo.schema_catalog_table (table_name, description) VALUES
    (N'risk_features', N'Per-case structured risk features at ingest. Join on record_uuid to ingest/decisions.'),
    (N'risk_ingest_records', N'Ingested cases with review_outcome passed/rejected/frozen and optional notes.'),
    (N'risk_decisions', N'Audit decision history per request_id: pass, reject, freeze.'),
    (N'risk_feature_binned', N'Binned one-hot feature vectors per calibration_id for ML.'),
    (N'qa_conversation', N'Agentic Q&A conversation headers.'),
    (N'orchestrator_run', N'Agent orchestrator run: question, workflow JSON, status, answer.');
END;

-- risk_features columns (sample)
IF NOT EXISTS (SELECT 1 FROM dbo.schema_catalog_column WHERE table_name = N'risk_features' AND column_name = N'record_uuid')
BEGIN
    INSERT INTO dbo.schema_catalog_column (table_name, column_name, data_type, description, is_nullable) VALUES
    (N'risk_features', N'record_uuid', N'uniqueidentifier', N'Primary case id shared with ingest.', 0),
    (N'risk_features', N'scenario', N'nvarchar', N'Use-case id e.g. login_anomaly, geo_mismatch.', 1),
    (N'risk_features', N'user_id', N'nvarchar', N'Synthetic user identifier.', 1),
    (N'risk_features', N'withdraw_amount', N'decimal', N'Withdrawal amount for the case.', 1),
    (N'risk_features', N'features_json', N'nvarchar(max)', N'Full JSON blob of all features.', 1),
    (N'risk_ingest_records', N'record_uuid', N'uniqueidentifier', N'Case id.', 0),
    (N'risk_ingest_records', N'review_outcome', N'nvarchar', N'passed | rejected | frozen.', 0),
    (N'risk_ingest_records', N'created_at', N'datetime2', N'UTC ingest time.', 0),
    (N'risk_decisions', N'request_id', N'nvarchar', N'Business request id.', 0),
    (N'risk_decisions', N'decision', N'nvarchar', N'pass | reject | freeze.', 0),
    (N'orchestrator_run', N'run_id', N'uniqueidentifier', N'Orchestrator run id.', 0),
    (N'orchestrator_run', N'status', N'nvarchar', N'PENDING | RUNNING | COMPLETED | FAILED.', 0),
    (N'orchestrator_run', N'question', N'nvarchar(max)', N'User question text.', 0);
END;
