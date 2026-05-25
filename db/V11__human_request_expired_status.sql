-- Allow EXPIRED status for cancelled human-in-the-loop requests

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_human_request_status'
      AND parent_object_id = OBJECT_ID(N'dbo.orchestrator_human_request')
)
BEGIN
    ALTER TABLE dbo.orchestrator_human_request DROP CONSTRAINT CK_human_request_status;
END;

ALTER TABLE dbo.orchestrator_human_request
    ADD CONSTRAINT CK_human_request_status
    CHECK (status IN (N'WAITING', N'ANSWERED', N'EXPIRED'));
