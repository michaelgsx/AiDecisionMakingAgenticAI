package com.aidecision.agentic.service;

import com.aidecision.agentic.repository.UserTableAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves which schema_catalog tables a user may query. SQL tools must call this before reading
 * catalog descriptions or generating SQL. When no caller userId is provided, {@link #DEFAULT_USER_ID}
 * is used (seeded in V19 with all enabled tables).
 */
@Service
public class UserTableAccessService {

    public static final String DEFAULT_USER_ID = "admin";

    private final UserTableAccessRepository accessRepo;
    private final SchemaCatalogService catalog;

    public UserTableAccessService(UserTableAccessRepository accessRepo, SchemaCatalogService catalog) {
        this.accessRepo = accessRepo;
        this.catalog = catalog;
    }

    /** Normalize blank/null to the default admin user. */
    public String resolveUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return userId.trim();
    }

    /**
     * Tables this user may access. Always reads {@code user_table_access} first, then keeps only
     * names still enabled in the schema catalog. Admin is seeded with all enabled tables in V19.
     */
    @Transactional(readOnly = true)
    public List<String> allowedTableNames(String userId) {
        String effective = resolveUserId(userId);
        List<String> fromAcl = accessRepo.findDistinctTableNamesByUserId(effective);
        if (fromAcl.isEmpty()) {
            return List.of();
        }
        Set<String> enabled = new HashSet<>(catalog.enabledTableNames());
        List<String> out = new ArrayList<>();
        for (String name : fromAcl) {
            if (enabled.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /** Intersect candidate table names with the user's ACL (catalog-enabled names only). */
    @Transactional(readOnly = true)
    public List<String> intersectCandidates(String userId, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return allowedTableNames(userId);
        }
        Set<String> allowed = new HashSet<>(allowedTableNames(userId));
        return candidates.stream().filter(allowed::contains).toList();
    }
}
