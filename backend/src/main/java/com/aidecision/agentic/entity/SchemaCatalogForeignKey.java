package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "schema_catalog_foreign_key")
public class SchemaCatalogForeignKey {

    @Id
    @Column(name = "fk_id")
    private UUID fkId;

    @Column(name = "from_schema", length = 64, nullable = false)
    private String fromSchema = "dbo";

    @Column(name = "from_table", length = 128, nullable = false)
    private String fromTable;

    @Column(name = "from_column", length = 128, nullable = false)
    private String fromColumn;

    @Column(name = "to_schema", length = 64, nullable = false)
    private String toSchema = "dbo";

    @Column(name = "to_table", length = 128, nullable = false)
    private String toTable;

    @Column(name = "to_column", length = 128, nullable = false)
    private String toColumn;

    @Column(name = "description", length = 1000, nullable = false)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public UUID getFkId() { return fkId; }
    public void setFkId(UUID fkId) { this.fkId = fkId; }

    public String getFromSchema() { return fromSchema; }
    public void setFromSchema(String fromSchema) { this.fromSchema = fromSchema; }

    public String getFromTable() { return fromTable; }
    public void setFromTable(String fromTable) { this.fromTable = fromTable; }

    public String getFromColumn() { return fromColumn; }
    public void setFromColumn(String fromColumn) { this.fromColumn = fromColumn; }

    public String getToSchema() { return toSchema; }
    public void setToSchema(String toSchema) { this.toSchema = toSchema; }

    public String getToTable() { return toTable; }
    public void setToTable(String toTable) { this.toTable = toTable; }

    public String getToColumn() { return toColumn; }
    public void setToColumn(String toColumn) { this.toColumn = toColumn; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
