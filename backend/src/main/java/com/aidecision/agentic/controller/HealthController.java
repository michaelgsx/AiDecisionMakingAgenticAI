package com.aidecision.agentic.controller;

import com.aidecision.agentic.dto.HealthResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return new HealthResponse(true, "azure-sql");
    }
}
