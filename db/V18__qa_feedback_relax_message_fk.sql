-- Thumbs feedback on orchestrator answers references the run (run_id), not a qa_message row.
-- The orchestrator never inserts a qa_message for its final answer, so the frontend sends the
-- runId as message_id. The hard FK_qa_feedback_message constraint rejected that insert
-- ("conflicted with the FOREIGN KEY constraint FK_qa_feedback_message"). Relax message_id so
-- feedback can be stored against an orchestrator run; run_id remains the durable link.

IF EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_qa_feedback_message' AND parent_object_id = OBJECT_ID(N'dbo.qa_feedback')
)
BEGIN
    ALTER TABLE dbo.qa_feedback DROP CONSTRAINT FK_qa_feedback_message;
END;

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.qa_feedback') AND name = N'message_id' AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.qa_feedback ALTER COLUMN message_id UNIQUEIDENTIFIER NULL;
END;
