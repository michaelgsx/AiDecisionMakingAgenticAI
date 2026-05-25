package com.aidecision.agentic.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReadOnlySqlExecutor {

    private final JdbcTemplate jdbc;
    private final ReadOnlySqlValidator validator;

    public ReadOnlySqlExecutor(JdbcTemplate jdbc, ReadOnlySqlValidator validator) {
        this.jdbc = jdbc;
        this.validator = validator;
    }

    public List<Map<String, Object>> executeSelect(String sql, int maxRows) {
        validator.validate(sql);
        int limit = Math.min(Math.max(maxRows, 1), 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query(sql.trim(), rs -> {
            if (rows.size() >= limit) {
                return;
            }
            var meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        });
        return rows;
    }
}
