-- Human evaluation queue for completed Q&A runs (accept / reject for further review)

IF OBJECT_ID(N'dbo.qa_evaluation', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.qa_evaluation (
        evaluation_id   UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        run_id          UNIQUEIDENTIFIER NOT NULL,
        question        NVARCHAR(MAX) NOT NULL,
        answer_text     NVARCHAR(MAX) NOT NULL,
        review_status   NVARCHAR(16) NOT NULL,
        reviewer_id     NVARCHAR(128) NULL,
        comment         NVARCHAR(2000) NULL,
        created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        reviewed_at     DATETIME2 NULL,
        CONSTRAINT FK_qa_evaluation_run FOREIGN KEY (run_id)
            REFERENCES dbo.orchestrator_run(run_id) ON DELETE CASCADE,
        CONSTRAINT UQ_qa_evaluation_run UNIQUE (run_id),
        CONSTRAINT CK_qa_evaluation_status CHECK (review_status IN (N'PENDING', N'ACCEPTED', N'REJECTED'))
    );
    CREATE INDEX IX_qa_evaluation_status ON dbo.qa_evaluation(review_status, created_at DESC);
END;
