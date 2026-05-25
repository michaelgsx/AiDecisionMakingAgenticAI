-- Full schema_catalog descriptions (NL2SQL) + demo Q&A / evaluation rows
-- AI-generated metadata for development; align column names with AiDecisionMakingBackend schema.

-- ========== schema_catalog_table ==========
MERGE dbo.schema_catalog_table AS t
USING (VALUES
    (N'risk_features', N'One row per risk case (request). Core fields denormalized; full payload in features_json. Join key: request_id (CHAR(36)).'),
    (N'risk_ingest_records', N'Cases ingested for RAG/search. review_outcome: passed | rejected | frozen. Join to risk_features via record_uuid = request_id.'),
    (N'risk_decisions', N'Audit trail of pass | reject | freeze per request_id. Multiple rows allowed (e.g. freeze then pass).'),
    (N'risk_embeddings', N'Vector embeddings per request_id and embedding_type (feature, conversation, graph).'),
    (N'risk_feature_bin_calibrations', N'Quantile bin calibration metadata for ML feature binning.'),
    (N'risk_feature_bin_edges', N'Per-feature bin edge definitions for a calibration_id.'),
    (N'risk_feature_binned', N'Binned feature vectors per request_id and calibration_id.'),
    (N'activity_log', N'Tamper-evident per-user activity chain (hash linked).'),
    (N'qa_conversation', N'Agentic Q&A session header (optional user_id, title).'),
    (N'qa_message', N'Messages in a conversation: role user | assistant | system.'),
    (N'qa_feedback', N'Thumbs feedback on a message; may link run_id for orchestrator answers.'),
    (N'orchestrator_tool', N'Registered agent tools (name, schemas, SYNC/ASYNC).'),
    (N'orchestrator_run', N'One agent workflow run per user question.'),
    (N'orchestrator_step', N'DAG step instance: tool, status, inputs/outputs JSON.'),
    (N'orchestrator_human_request', N'Async human-in-the-loop approval during a run.'),
    (N'qa_evaluation', N'Post-run human review queue: accept/reject final Q&A.')
) AS s(table_name, description)
ON t.table_name = s.table_name
WHEN MATCHED THEN
    UPDATE SET description = s.description, enabled = 1, updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (table_name, description, enabled) VALUES (s.table_name, s.description, 1);

-- Disable obsolete/wrong catalog rows from early seeds
UPDATE dbo.schema_catalog_column SET enabled = 0
WHERE table_name = N'risk_features' AND column_name = N'record_uuid';

-- ========== schema_catalog_column (MERGE per table) ==========
MERGE dbo.schema_catalog_column AS c
USING (VALUES
    -- risk_features
    (N'risk_features', N'id', N'bigint', N'Surrogate primary key.', 0, N'1'),
    (N'risk_features', N'request_id', N'char(36)', N'Business case UUID string; unique; joins ingest/decisions/embeddings.', 0, N'a1b2c3d4-e5f6-7890-abcd-ef1234567890'),
    (N'risk_features', N'source', N'varchar(20)', N'ingest | risk-similarity — how the row was created.', 0, N'ingest'),
    (N'risk_features', N'scenario', N'nvarchar(200)', N'Risk scenario label e.g. login_anomaly, withdrawal_spike.', 1, N'withdrawal_spike'),
    (N'risk_features', N'transaction_id', N'nvarchar(200)', N'External transaction reference.', 1, N'txn-10042'),
    (N'risk_features', N'user_id', N'nvarchar(200)', N'Customer / user identifier.', 1, N'user-demo-001'),
    (N'risk_features', N'device_id', N'nvarchar(200)', N'Device fingerprint id.', 1, NULL),
    (N'risk_features', N'country_code', N'nvarchar(10)', N'ISO country code for geo risk.', 1, N'US'),
    (N'risk_features', N'withdraw_amount', N'decimal(18,2)', N'Withdrawal amount in account currency.', 1, N'15000.00'),
    (N'risk_features', N'deposit_amount', N'decimal(18,2)', N'Deposit amount.', 1, NULL),
    (N'risk_features', N'total_amount', N'decimal(18,2)', N'Combined or net transaction amount.', 1, NULL),
    (N'risk_features', N'features_json', N'nvarchar(max)', N'Full feature JSON document for ML/RAG.', 0, N'{"risk_score":0.82}'),
    (N'risk_features', N'created_at', N'datetime2', N'UTC row creation time.', 0, NULL),
    -- risk_ingest_records
    (N'risk_ingest_records', N'id', N'bigint', N'Surrogate PK.', 0, NULL),
    (N'risk_ingest_records', N'record_uuid', N'char(36)', N'Same as risk_features.request_id for linked cases.', 0, NULL),
    (N'risk_ingest_records', N'review_outcome', N'varchar(20)', N'passed | rejected | frozen — human/ops review label.', 0, N'frozen'),
    (N'risk_ingest_records', N'text', N'nvarchar(max)', N'Free-text case notes for search/RAG.', 1, N'Large withdrawal after new device'),
    (N'risk_ingest_records', N'metadata', N'nvarchar(max)', N'JSON metadata blob for Azure AI Search.', 1, N'{"user_id":"user-demo-001"}'),
    (N'risk_ingest_records', N'created_at', N'datetime2', N'UTC ingest time.', 0, NULL),
    -- risk_decisions
    (N'risk_decisions', N'id', N'bigint', N'Surrogate PK.', 0, NULL),
    (N'risk_decisions', N'request_id', N'char(36)', N'FK to risk_features.request_id.', 0, NULL),
    (N'risk_decisions', N'decision', N'varchar(10)', N'pass | reject | freeze.', 0, N'freeze'),
    (N'risk_decisions', N'is_final', N'bit', N'Computed: 1 for pass/reject, 0 for freeze.', 0, N'0'),
    (N'risk_decisions', N'reason', N'nvarchar(max)', N'Analyst or system reason text.', 1, NULL),
    (N'risk_decisions', N'decided_by', N'nvarchar(200)', N'Actor id or service name.', 1, N'agentic-ai'),
    (N'risk_decisions', N'created_at', N'datetime2', N'UTC decision time.', 0, NULL),
    -- risk_embeddings
    (N'risk_embeddings', N'request_id', N'char(36)', N'Case id.', 0, NULL),
    (N'risk_embeddings', N'embedding_type', N'varchar(30)', N'feature | conversation | graph.', 0, N'feature'),
    (N'risk_embeddings', N'embedding_json', N'nvarchar(max)', N'Serialized embedding vector JSON.', 0, NULL),
    (N'risk_embeddings', N'dimensions', N'int', N'Vector length.', 0, N'1536'),
    (N'risk_embeddings', N'model_name', N'nvarchar(200)', N'Embedding model deployment name.', 0, NULL),
    -- orchestrator_run
    (N'orchestrator_run', N'run_id', N'uniqueidentifier', N'Primary key for polling / feedback.', 0, NULL),
    (N'orchestrator_run', N'conversation_id', N'uniqueidentifier', N'Optional link to qa_conversation.', 1, NULL),
    (N'orchestrator_run', N'user_id', N'nvarchar(128)', N'End-user id if provided.', 1, NULL),
    (N'orchestrator_run', N'question', N'nvarchar(max)', N'Natural language user question.', 0, NULL),
    (N'orchestrator_run', N'status', N'nvarchar(32)', N'PENDING | PLANNING | RUNNING | COMPLETED | FAILED | CANCELLED.', 0, N'COMPLETED'),
    (N'orchestrator_run', N'answer_text', N'nvarchar(max)', N'Final synthesized answer when COMPLETED.', 1, NULL),
    (N'orchestrator_run', N'workflow_json', N'nvarchar(max)', N'Planned DAG JSON.', 1, NULL),
    (N'orchestrator_run', N'created_at', N'datetime2', N'Run start UTC.', 0, NULL),
    -- qa_evaluation
    (N'qa_evaluation', N'evaluation_id', N'uniqueidentifier', N'Human review row id.', 0, NULL),
    (N'qa_evaluation', N'run_id', N'uniqueidentifier', N'FK orchestrator_run.', 0, NULL),
    (N'qa_evaluation', N'question', N'nvarchar(max)', N'Copy of question for review UI.', 0, NULL),
    (N'qa_evaluation', N'answer_text', N'nvarchar(max)', N'Copy of answer for review UI.', 0, NULL),
    (N'qa_evaluation', N'review_status', N'nvarchar(16)', N'PENDING | ACCEPTED | REJECTED.', 0, N'PENDING'),
    (N'qa_evaluation', N'reviewer_id', N'nvarchar(128)', N'Human reviewer id after review.', 1, NULL),
    (N'qa_evaluation', N'comment', N'nvarchar(2000)', N'Optional review comment.', 1, NULL)
) AS s(table_name, column_name, data_type, description, is_nullable, sample_hint)
ON c.table_name = s.table_name AND c.column_name = s.column_name
WHEN MATCHED THEN
    UPDATE SET data_type = s.data_type, description = s.description, is_nullable = s.is_nullable,
               sample_hint = s.sample_hint, enabled = 1
WHEN NOT MATCHED THEN
    INSERT (table_name, column_name, data_type, description, is_nullable, sample_hint, enabled)
    VALUES (s.table_name, s.column_name, s.data_type, s.description, s.is_nullable, s.sample_hint, 1);

-- ========== Demo risk rows (only if tables exist and empty) ==========
IF OBJECT_ID(N'dbo.risk_features', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.risk_features)
BEGIN
    INSERT INTO dbo.risk_features (request_id, source, scenario, transaction_id, user_id, country_code,
        withdraw_amount, features_json)
    VALUES
    ('11111111-1111-1111-1111-111111111101', 'ingest', N'withdrawal_spike', N'txn-demo-101', N'user-demo-001', N'US',
        15000.00, N'{"risk_score":0.91,"device_new":true}'),
    ('11111111-1111-1111-1111-111111111102', 'ingest', N'login_anomaly', N'txn-demo-102', N'user-demo-002', N'GB',
        500.00, N'{"risk_score":0.72,"ip_country_mismatch":true}'),
    ('11111111-1111-1111-1111-111111111103', 'risk-similarity', N'geo_mismatch', N'txn-demo-103', N'user-demo-003', N'SG',
        2200.00, N'{"risk_score":0.65}');
END;

IF OBJECT_ID(N'dbo.risk_ingest_records', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.risk_ingest_records)
BEGIN
    INSERT INTO dbo.risk_ingest_records (record_uuid, review_outcome, [text], metadata)
    VALUES
    ('11111111-1111-1111-1111-111111111101', 'frozen',
        N'Large withdrawal shortly after device change; similar cases were frozen.',
        N'{"user_id":"user-demo-001","scenario":"withdrawal_spike"}'),
    ('11111111-1111-1111-1111-111111111102', 'rejected',
        N'Impossible travel login pattern.',
        N'{"user_id":"user-demo-002","scenario":"login_anomaly"}'),
    ('11111111-1111-1111-1111-111111111103', 'passed',
        N'Low-value transfer with consistent history.',
        N'{"user_id":"user-demo-003","scenario":"geo_mismatch"}');
END;

IF OBJECT_ID(N'dbo.risk_decisions', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.risk_decisions)
BEGIN
    INSERT INTO dbo.risk_decisions (request_id, decision, reason, decided_by)
    VALUES
    ('11111111-1111-1111-1111-111111111101', 'freeze', N'Pending analyst review', N'demo-seed'),
    ('11111111-1111-1111-1111-111111111102', 'reject', N'Automated policy match', N'demo-seed'),
    ('11111111-1111-1111-1111-111111111103', 'pass', N'Within limits', N'demo-seed');
END;

-- ========== Demo agentic Q&A + evaluations ==========
DECLARE @conv1 UNIQUEIDENTIFIER = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001';
DECLARE @conv2 UNIQUEIDENTIFIER = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002';
DECLARE @run1 UNIQUEIDENTIFIER = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00001';
DECLARE @run2 UNIQUEIDENTIFIER = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00002';
DECLARE @run3 UNIQUEIDENTIFIER = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbb00003';
DECLARE @msg1 UNIQUEIDENTIFIER = 'cccccccc-cccc-4ccc-8ccc-ccccccccc001';
DECLARE @msg2 UNIQUEIDENTIFIER = 'cccccccc-cccc-4ccc-8ccc-ccccccccc002';

IF OBJECT_ID(N'dbo.qa_conversation', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_conversation WHERE conversation_id = @conv1)
        INSERT INTO dbo.qa_conversation (conversation_id, user_id, title)
        VALUES (@conv1, N'reviewer-demo', N'Withdrawal freeze policy');
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_conversation WHERE conversation_id = @conv2)
        INSERT INTO dbo.qa_conversation (conversation_id, user_id, title)
        VALUES (@conv2, N'reviewer-demo', N'Similar case lookup');
END;

IF OBJECT_ID(N'dbo.qa_message', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_message WHERE message_id = @msg1)
        INSERT INTO dbo.qa_message (message_id, conversation_id, role, content, model_name)
        VALUES (@msg1, @conv1, N'user', N'Should we freeze this withdrawal?', NULL);
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_message WHERE message_id = @msg2)
        INSERT INTO dbo.qa_message (message_id, conversation_id, role, content, model_name)
        VALUES (@msg2, @conv1, N'assistant', N'Based on similar frozen cases, recommend freeze pending review.', N'ai-rag-agentic-ai');
END;

IF OBJECT_ID(N'dbo.orchestrator_run', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.orchestrator_run WHERE run_id = @run1)
        INSERT INTO dbo.orchestrator_run (run_id, conversation_id, user_id, question, status, answer_text, workflow_json)
        VALUES (@run1, @conv1, N'reviewer-demo',
            N'What similar cases support freezing this withdrawal?', N'COMPLETED',
            N'Two similar cases (demo-101 pattern) were frozen for withdrawal_spike with new device. Recommend freeze pending senior review.',
            N'{"steps":[{"id":"s1","tool":"data_acquisition"},{"id":"s2","tool":"ai_decision_rag"},{"id":"s3","tool":"llm_answer"}]}');
    IF NOT EXISTS (SELECT 1 FROM dbo.orchestrator_run WHERE run_id = @run2)
        INSERT INTO dbo.orchestrator_run (run_id, conversation_id, user_id, question, status, answer_text)
        VALUES (@run2, @conv1, N'reviewer-demo',
            N'How many rejected login_anomaly cases occurred last week?', N'COMPLETED',
            N'(Demo) NL2SQL would query risk_ingest_records; sample shows at least 1 rejected login_anomaly case in seed data.');
    IF NOT EXISTS (SELECT 1 FROM dbo.orchestrator_run WHERE run_id = @run3)
        INSERT INTO dbo.orchestrator_run (run_id, conversation_id, user_id, question, status, answer_text)
        VALUES (@run3, @conv2, N'reviewer-demo',
            N'Summarize pass vs reject rate for geo_mismatch scenario.', N'COMPLETED',
            N'(Demo) In seed data, geo_mismatch sample case is passed; expand with real aggregates via natural_language_to_sql.');
END;

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_evaluation WHERE run_id = @run1)
        INSERT INTO dbo.qa_evaluation (run_id, question, answer_text, review_status)
        VALUES (@run1, N'What similar cases support freezing this withdrawal?',
            N'Two similar cases (demo-101 pattern) were frozen for withdrawal_spike with new device. Recommend freeze pending senior review.',
            N'PENDING');
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_evaluation WHERE run_id = @run2)
        INSERT INTO dbo.qa_evaluation (run_id, question, answer_text, review_status, reviewer_id, comment, reviewed_at)
        VALUES (@run2, N'How many rejected login_anomaly cases occurred last week?',
            N'(Demo) NL2SQL would query risk_ingest_records; sample shows at least 1 rejected login_anomaly case in seed data.',
            N'ACCEPTED', N'reviewer-demo', N'Looks reasonable for pilot', SYSUTCDATETIME());
    IF NOT EXISTS (SELECT 1 FROM dbo.qa_evaluation WHERE run_id = @run3)
        INSERT INTO dbo.qa_evaluation (run_id, question, answer_text, review_status, reviewer_id, comment, reviewed_at)
        VALUES (@run3, N'Summarize pass vs reject rate for geo_mismatch scenario.',
            N'(Demo) In seed data, geo_mismatch sample case is passed; expand with real aggregates via natural_language_to_sql.',
            N'REJECTED', N'reviewer-demo', N'Needs SQL evidence', SYSUTCDATETIME());
END;
