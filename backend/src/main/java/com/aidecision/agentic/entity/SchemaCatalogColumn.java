package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "schema_catalog_column")
public class SchemaCatalogColumn {

    @Id
    @Column(name = "column_id")
    private UUID columnId;

    @Column(name = "table_name", length = 128, nullable = false)
    private String tableName;

    @Column(name = "column_name", length = 128, nullable = false)
    private String columnName;

    @Column(name = "data_type", length = 64)
    private String dataType;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "is_nullable")
    private Boolean nullable;

    @Column(name = "sample_hint", length = 500)
    private String sampleHint;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public UUID getColumnId() { return columnId; }
    public void setColumnId(UUID columnId) { this.columnId = columnId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getNullable() { return nullable; }
    public void setNullable(Boolean nullable) { this.nullable = nullable; }

    public String getSampleHint() { return sampleHint; }
    public void setSampleHint(String sampleHint) { this.sampleHint = sampleHint; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
