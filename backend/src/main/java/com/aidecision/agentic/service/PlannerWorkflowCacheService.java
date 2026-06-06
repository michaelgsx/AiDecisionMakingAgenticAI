package com.aidecision.agentic.service;

import com.aidecision.agentic.entity.PlannerWorkflowCache;
import com.aidecision.agentic.orchestrator.PlannerPrompt;
import com.aidecision.agentic.repository.PlannerWorkflowCacheRepository;
import com.aidecision.agentic.util.LogSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PlannerWorkflowCacheService {

    private static final Logger log = LoggerFactory.getLogger(PlannerWorkflowCacheService.class);

    private final PlannerWorkflowCacheRepository cacheRepo;
    private final ObjectMapper mapper;

    public PlannerWorkflowCacheService(PlannerWorkflowCacheRepository cacheRepo, ObjectMapper mapper) {
        this.cacheRepo = cacheRepo;
        this.mapper = mapper;
    }

    public Optional<PlannerWorkflowCache> findByQuestion(String question) {
        String hash = questionHash(question);
        return cacheRepo.findByQuestionHash(hash);
    }

    /** False when planner prompt text changed (e.g. new compound-question rules) — caller should replan. */
    public boolean matchesPlannerPrompt(PlannerWorkflowCache cached, PlannerPrompt current) {
        if (cached == null || current == null) {
            return false;
        }
        String storedJson = cached.getPlannerPrompt();
        if (storedJson == null || storedJson.isBlank()) {
            return false;
        }
        try {
            JsonNode stored = mapper.readTree(storedJson);
            return stored.path("systemPrompt").asText("").equals(current.systemPrompt())
                    && stored.path("userPrompt").asText("").equals(current.userPrompt())
                    && stored.path("outputJsonSchema").asText("").equals(current.outputJsonSchema());
        } catch (Exception e) {
            log.warn("Could not parse stored planner prompt hash={}: {}",
                    cached.getQuestionHash() == null ? "?" : cached.getQuestionHash().substring(0, 8),
                    LogSanitizer.message(e.getMessage()));
            return false;
        }
    }

    @Transactional
    public PlannerWorkflowCache save(String question, PlannerPrompt prompt, String workflowJson) throws Exception {
        String hash = questionHash(question);
        String plannerPromptJson = serializePrompt(prompt);

        PlannerWorkflowCache row = cacheRepo.findByQuestionHash(hash).orElseGet(PlannerWorkflowCache::new);
        row.setQuestion(question == null ? "" : question.trim());
        row.setQuestionHash(hash);
        row.setPlannerPrompt(plannerPromptJson);
        row.setWorkflowJson(workflowJson);

        PlannerWorkflowCache saved = cacheRepo.save(row);
        log.info("Planner workflow cache saved question={} hash={} workflow={}",
                LogSanitizer.question(question),
                hash.substring(0, 8) + "...",
                LogSanitizer.jsonSummary(workflowJson));
        return saved;
    }

    /** Trim, collapse whitespace, lowercase — used for cache key hashing. */
    public static String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String questionHash(String question) {
        String normalized = normalizeQuestion(question);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String serializePrompt(PlannerPrompt prompt) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("systemPrompt", prompt.systemPrompt());
        node.put("userPrompt", prompt.userPrompt());
        node.put("outputJsonSchema", prompt.outputJsonSchema());
        return mapper.writeValueAsString(node);
    }
}
