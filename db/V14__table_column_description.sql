-- LLM-facing table/column metadata for SQL tools (NL→SQL).
-- Populated from sys.tables / sys.columns; merges text from schema_catalog_* when present.
-- Excludes table_description and column_description themselves.

IF OBJECT_ID(N'dbo.table_description', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.table_description (
        schema_name   NVARCHAR(64)  NOT NULL CONSTRAINT DF_table_description_schema DEFAULT N'dbo',
        table_name    NVARCHAR(128) NOT NULL,
        description   NVARCHAR(2000) NOT NULL,
        group_id      NVARCHAR(64)  NULL,
        enabled       BIT           NOT NULL CONSTRAINT DF_table_description_enabled DEFAULT 1,
        created_at    DATETIME2     NOT NULL CONSTRAINT DF_table_description_created DEFAULT SYSUTCDATETIME(),
        updated_at    DATETIME2     NOT NULL CONSTRAINT DF_table_description_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_table_description PRIMARY KEY (schema_name, table_name)
    );

    CREATE INDEX IX_table_description_group ON dbo.table_description (group_id, enabled);
END;

IF OBJECT_ID(N'dbo.column_description', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.column_description (
        column_description_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_column_description_id DEFAULT NEWID(),
        schema_name           NVARCHAR(64)  NOT NULL CONSTRAINT DF_column_description_schema DEFAULT N'dbo',
        table_name            NVARCHAR(128) NOT NULL,
        column_name           NVARCHAR(128) NOT NULL,
        data_type             NVARCHAR(128) NULL,
        column_description    NVARCHAR(2000) NOT NULL,
        is_nullable           BIT           NULL,
        ordinal_position      INT           NULL,
        enabled               BIT           NOT NULL CONSTRAINT DF_column_description_enabled DEFAULT 1,
        created_at            DATETIME2     NOT NULL CONSTRAINT DF_column_description_created DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2     NOT NULL CONSTRAINT DF_column_description_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_column_description PRIMARY KEY (column_description_id),
        CONSTRAINT UQ_column_description_col UNIQUE (schema_name, table_name, column_name),
        CONSTRAINT FK_column_description_table FOREIGN KEY (schema_name, table_name)
            REFERENCES dbo.table_description (schema_name, table_name) ON DELETE CASCADE
    );

    CREATE INDEX IX_column_description_table ON dbo.column_description (schema_name, table_name, enabled);
END;

-- ========== Seed / refresh table_description from all user tables ==========
MERGE dbo.table_description AS td
USING (
    SELECT
        s.name AS schema_name,
        t.name AS table_name,
        COALESCE(
            cat.description,
            N'Table ' + s.name + N'.' + t.name + N' in database ai-rag-db-1.'
        ) AS description,
        CASE
            WHEN t.name LIKE N'risk_%' THEN N'risk'
            WHEN t.name LIKE N'orchestrator_%' THEN N'orchestrator'
            WHEN t.name LIKE N'qa_%' THEN N'qa'
            WHEN t.name LIKE N'schema_catalog_%' THEN N'schema_catalog'
            WHEN t.name = N'activity_log' THEN N'audit'
            WHEN t.name LIKE N'risk_feature_bin%' THEN N'ml_calibration'
            ELSE N'general'
        END AS group_id
    FROM sys.tables t
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    LEFT JOIN dbo.schema_catalog_table cat
        ON cat.table_name = t.name
       AND cat.schema_name = s.name
    WHERE t.is_ms_shipped = 0
      AND t.name NOT IN (N'table_description', N'column_description')
) AS src
ON td.schema_name = src.schema_name AND td.table_name = src.table_name
WHEN MATCHED THEN
    UPDATE SET
        description = src.description,
        group_id    = src.group_id,
        enabled     = 1,
        updated_at  = SYSUTCDATETIME()
WHEN NOT MATCHED BY TARGET THEN
    INSERT (schema_name, table_name, description, group_id, enabled)
    VALUES (src.schema_name, src.table_name, src.description, src.group_id, 1);

-- Disable catalog rows for tables removed from the database
UPDATE td
SET enabled = 0, updated_at = SYSUTCDATETIME()
FROM dbo.table_description td
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.tables t
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE s.name = td.schema_name
      AND t.name = td.table_name
      AND t.is_ms_shipped = 0
);

-- ========== Seed / refresh column_description from all user columns ==========
MERGE dbo.column_description AS cd
USING (
    SELECT
        s.name AS schema_name,
        t.name AS table_name,
        c.name AS column_name,
        CASE
            WHEN ty.name IN (N'varchar', N'nvarchar', N'char', N'nchar') THEN
                ty.name + N'(' + CASE
                    WHEN c.max_length = -1 THEN N'max'
                    WHEN ty.name LIKE N'n%' THEN CAST(c.max_length / 2 AS NVARCHAR(16))
                    ELSE CAST(c.max_length AS NVARCHAR(16))
                END + N')'
            WHEN ty.name IN (N'decimal', N'numeric') THEN
                ty.name + N'(' + CAST(c.precision AS NVARCHAR(8)) + N',' + CAST(c.scale AS NVARCHAR(8)) + N')'
            ELSE ty.name
        END AS data_type,
        COALESCE(
            sc.description,
            N'Column ' + c.name + N' on ' + s.name + N'.' + t.name
            + N' (' + ty.name
            + CASE WHEN c.is_nullable = 1 THEN N', nullable' ELSE N', not null' END
            + N').'
        ) AS column_description,
        CAST(c.is_nullable AS BIT) AS is_nullable,
        c.column_id AS ordinal_position
    FROM sys.columns c
    INNER JOIN sys.tables t ON c.object_id = t.object_id
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    INNER JOIN sys.types ty ON c.user_type_id = ty.user_type_id AND ty.is_user_defined = 0
    LEFT JOIN dbo.schema_catalog_column sc
        ON sc.table_name = t.name
       AND sc.column_name = c.name
    WHERE t.is_ms_shipped = 0
      AND t.name NOT IN (N'table_description', N'column_description')
) AS src
ON cd.schema_name = src.schema_name
 AND cd.table_name = src.table_name
 AND cd.column_name = src.column_name
WHEN MATCHED THEN
    UPDATE SET
        data_type          = src.data_type,
        column_description = src.column_description,
        is_nullable        = src.is_nullable,
        ordinal_position   = src.ordinal_position,
        enabled            = 1,
        updated_at         = SYSUTCDATETIME()
WHEN NOT MATCHED BY TARGET THEN
    INSERT (schema_name, table_name, column_name, data_type, column_description, is_nullable, ordinal_position, enabled)
    VALUES (src.schema_name, src.table_name, src.column_name, src.data_type, src.column_description,
            src.is_nullable, src.ordinal_position, 1);

-- Disable column rows removed from the database
UPDATE cd
SET enabled = 0, updated_at = SYSUTCDATETIME()
FROM dbo.column_description cd
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.columns c
    INNER JOIN sys.tables t ON c.object_id = t.object_id
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE s.name = cd.schema_name
      AND t.name = cd.table_name
      AND c.name = cd.column_name
      AND t.is_ms_shipped = 0
);

-- Optional: call after DDL changes to resync metadata (same logic as above)
IF OBJECT_ID(N'dbo.usp_refresh_table_column_descriptions', N'P') IS NOT NULL
    DROP PROCEDURE dbo.usp_refresh_table_column_descriptions;

GO

CREATE PROCEDURE dbo.usp_refresh_table_column_descriptions
AS
BEGIN
    SET NOCOUNT ON;

    MERGE dbo.table_description AS td
    USING (
        SELECT
            s.name AS schema_name,
            t.name AS table_name,
            COALESCE(
                cat.description,
                N'Table ' + s.name + N'.' + t.name + N' in database ai-rag-db-1.'
            ) AS description,
            CASE
                WHEN t.name LIKE N'risk_%' THEN N'risk'
                WHEN t.name LIKE N'orchestrator_%' THEN N'orchestrator'
                WHEN t.name LIKE N'qa_%' THEN N'qa'
                WHEN t.name LIKE N'schema_catalog_%' THEN N'schema_catalog'
                WHEN t.name = N'activity_log' THEN N'audit'
                WHEN t.name LIKE N'risk_feature_bin%' THEN N'ml_calibration'
                ELSE N'general'
            END AS group_id
        FROM sys.tables t
        INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
        LEFT JOIN dbo.schema_catalog_table cat
            ON cat.table_name = t.name AND cat.schema_name = s.name
        WHERE t.is_ms_shipped = 0
          AND t.name NOT IN (N'table_description', N'column_description')
    ) AS src
    ON td.schema_name = src.schema_name AND td.table_name = src.table_name
    WHEN MATCHED THEN
        UPDATE SET description = src.description, group_id = src.group_id, enabled = 1, updated_at = SYSUTCDATETIME()
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (schema_name, table_name, description, group_id, enabled)
        VALUES (src.schema_name, src.table_name, src.description, src.group_id, 1);

    MERGE dbo.column_description AS cd
    USING (
        SELECT
            s.name AS schema_name,
            t.name AS table_name,
            c.name AS column_name,
            CASE
                WHEN ty.name IN (N'varchar', N'nvarchar', N'char', N'nchar') THEN
                    ty.name + N'(' + CASE
                        WHEN c.max_length = -1 THEN N'max'
                        WHEN ty.name LIKE N'n%' THEN CAST(c.max_length / 2 AS NVARCHAR(16))
                        ELSE CAST(c.max_length AS NVARCHAR(16))
                    END + N')'
                WHEN ty.name IN (N'decimal', N'numeric') THEN
                    ty.name + N'(' + CAST(c.precision AS NVARCHAR(8)) + N',' + CAST(c.scale AS NVARCHAR(8)) + N')'
                ELSE ty.name
            END AS data_type,
            COALESCE(
                sc.description,
                N'Column ' + c.name + N' on ' + s.name + N'.' + t.name
                + N' (' + ty.name
                + CASE WHEN c.is_nullable = 1 THEN N', nullable' ELSE N', not null' END
                + N').'
            ) AS column_description,
            CAST(c.is_nullable AS BIT) AS is_nullable,
            c.column_id AS ordinal_position
        FROM sys.columns c
        INNER JOIN sys.tables t ON c.object_id = t.object_id
        INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
        INNER JOIN sys.types ty ON c.user_type_id = ty.user_type_id AND ty.is_user_defined = 0
        LEFT JOIN dbo.schema_catalog_column sc
            ON sc.table_name = t.name AND sc.column_name = c.name
        WHERE t.is_ms_shipped = 0
          AND t.name NOT IN (N'table_description', N'column_description')
    ) AS src
    ON cd.schema_name = src.schema_name AND cd.table_name = src.table_name AND cd.column_name = src.column_name
    WHEN MATCHED THEN
        UPDATE SET
            data_type = src.data_type,
            column_description = src.column_description,
            is_nullable = src.is_nullable,
            ordinal_position = src.ordinal_position,
            enabled = 1,
            updated_at = SYSUTCDATETIME()
    WHEN NOT MATCHED BY TARGET THEN
        INSERT (schema_name, table_name, column_name, data_type, column_description, is_nullable, ordinal_position, enabled)
        VALUES (src.schema_name, src.table_name, src.column_name, src.data_type, src.column_description,
                src.is_nullable, src.ordinal_position, 1);
END;

GO
