package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.SchemaCatalogColumn;
import com.aidecision.agentic.entity.SchemaCatalogTable;
import com.aidecision.agentic.repository.SchemaCatalogColumnRepository;
import com.aidecision.agentic.repository.SchemaCatalogTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SchemaCatalogService {

    private final SchemaCatalogTableRepository tableRepo;
    private final SchemaCatalogColumnRepository columnRepo;

    public SchemaCatalogService(
            SchemaCatalogTableRepository tableRepo,
            SchemaCatalogColumnRepository columnRepo) {
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
    }

    @Transactional(readOnly = true)
    public String buildLlmCatalogText() {
        List<SchemaCatalogTable> tables = tableRepo.findByEnabledTrueOrderByTableNameAsc();
        if (tables.isEmpty()) {
            return "(schema catalog empty — run db migrations V4/V5)";
        }

        StringBuilder sb = new StringBuilder();
        for (SchemaCatalogTable t : tables) {
            sb.append("TABLE ").append(t.getSchemaName()).append(".").append(t.getTableName())
                    .append(": ").append(t.getDescription()).append("\n");
            List<SchemaCatalogColumn> cols =
                    columnRepo.findByTableNameAndEnabledTrueOrderByColumnNameAsc(t.getTableName());
            for (SchemaCatalogColumn c : cols) {
                sb.append("  - ").append(c.getColumnName());
                if (c.getDataType() != null) {
                    sb.append(" (").append(c.getDataType()).append(")");
                }
                sb.append(": ").append(c.getDescription());
                if (c.getSampleHint() != null && !c.getSampleHint().isBlank()) {
                    sb.append(" [example: ").append(c.getSampleHint()).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public List<String> enabledTableNames() {
        return tableRepo.findByEnabledTrueOrderByTableNameAsc().stream()
                .map(SchemaCatalogTable::getTableName)
                .collect(Collectors.toList());
    }
}
