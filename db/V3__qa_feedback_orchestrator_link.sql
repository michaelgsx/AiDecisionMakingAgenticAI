-- Link thumbs feedback to orchestrator runs (message_id may equal run_id for final answer)

IF COL_LENGTH('dbo.qa_feedback', 'run_id') IS NULL
BEGIN
    ALTER TABLE dbo.qa_feedback ADD run_id UNIQUEIDENTIFIER NULL;
END;

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.qa_feedback') AND name = 'conversation_id' AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.qa_feedback ALTER COLUMN conversation_id UNIQUEIDENTIFIER NULL;
END;
