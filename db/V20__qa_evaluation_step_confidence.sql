-- Per-step human evaluation + model confidence on every evaluation row.
-- Run-level rows use step_key = N'__run__'; step-level rows use workflow step_key.
-- Split into GO batches so SQL Server does not validate new columns before ADD runs.

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.qa_evaluation', 'evaluation_scope') IS NULL
        ALTER TABLE dbo.qa_evaluation
            ADD evaluation_scope NVARCHAR(8) NOT NULL
                CONSTRAINT DF_qa_evaluation_scope DEFAULT N'RUN';
END;
GO

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.qa_evaluation', 'step_key') IS NULL
        ALTER TABLE dbo.qa_evaluation
            ADD step_key NVARCHAR(64) NOT NULL
                CONSTRAINT DF_qa_evaluation_step_key DEFAULT N'__run__';
END;
GO

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.qa_evaluation', 'step_id') IS NULL
        ALTER TABLE dbo.qa_evaluation
            ADD step_id UNIQUEIDENTIFIER NULL;
END;
GO

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.qa_evaluation', 'tool_name') IS NULL
        ALTER TABLE dbo.qa_evaluation
            ADD tool_name NVARCHAR(64) NULL;
END;
GO

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.qa_evaluation', 'confidence') IS NULL
        ALTER TABLE dbo.qa_evaluation
            ADD confidence FLOAT NOT NULL
                CONSTRAINT DF_qa_evaluation_confidence DEFAULT 0.5;
END;
GO

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NOT NULL
BEGIN
    UPDATE dbo.qa_evaluation
    SET evaluation_scope = N'RUN',
        step_key = N'__run__'
    WHERE step_key IS NULL OR step_key = N'';

    IF EXISTS (
        SELECT 1 FROM sys.key_constraints
        WHERE name = N'UQ_qa_evaluation_run' AND parent_object_id = OBJECT_ID(N'dbo.qa_evaluation')
    )
        ALTER TABLE dbo.qa_evaluation DROP CONSTRAINT UQ_qa_evaluation_run;

    IF NOT EXISTS (
        SELECT 1 FROM sys.key_constraints
        WHERE name = N'UQ_qa_evaluation_run_step' AND parent_object_id = OBJECT_ID(N'dbo.qa_evaluation')
    )
        ALTER TABLE dbo.qa_evaluation
            ADD CONSTRAINT UQ_qa_evaluation_run_step UNIQUE (run_id, step_key);

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE name = N'CK_qa_evaluation_scope' AND parent_object_id = OBJECT_ID(N'dbo.qa_evaluation')
    )
        ALTER TABLE dbo.qa_evaluation
            ADD CONSTRAINT CK_qa_evaluation_scope
                CHECK (evaluation_scope IN (N'RUN', N'STEP'));

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE name = N'CK_qa_evaluation_confidence' AND parent_object_id = OBJECT_ID(N'dbo.qa_evaluation')
    )
        ALTER TABLE dbo.qa_evaluation
            ADD CONSTRAINT CK_qa_evaluation_confidence
                CHECK (confidence >= 0 AND confidence <= 1);
END;
GO
