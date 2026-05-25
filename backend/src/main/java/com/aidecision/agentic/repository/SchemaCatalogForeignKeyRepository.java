package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.SchemaCatalogForeignKey;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchemaCatalogForeignKeyRepository extends Repository<SchemaCatalogForeignKey, java.util.UUID> {

    @Query("""
            SELECT f FROM SchemaCatalogForeignKey f
            WHERE f.enabled = true
              AND (f.fromTable IN :tables OR f.toTable IN :tables)
            ORDER BY f.fromTable, f.fromColumn
            """)
    List<SchemaCatalogForeignKey> findEnabledInvolvingTables(@Param("tables") List<String> tables);
}
