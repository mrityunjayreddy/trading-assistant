package com.tradingservice.aiservice.repository;

import com.tradingservice.aiservice.domain.StrategyRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Repository for strategies table and strategy-service API calls.
 */
@Repository
public class StrategyRepository {

    private static final Logger log = LoggerFactory.getLogger(StrategyRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String strategyServiceUrl;

    public StrategyRepository(
            JdbcTemplate jdbcTemplate,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${services.strategy-service-url:http://localhost:8082}") String strategyServiceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.strategyServiceUrl = strategyServiceUrl;
    }

    /**
     * Find strategy by ID from local database.
     */
    public Optional<StrategyRecord> findById(UUID id) {
        String sql = """
            SELECT id, name, source, dsl, is_active, created_at, updated_at
            FROM strategies
            WHERE id = ?
        """;
        try {
            StrategyRecord record = jdbcTemplate.queryForObject(sql, new StrategyRowMapper(), id);
            return Optional.ofNullable(record);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validate a strategy DSL via dry-run (strategy-service API).
     */
    public boolean validateDryRun(String dsl) {
        try {
            ResponseEntity<Map> response = restClient.post()
                    .uri(strategyServiceUrl + "/api/strategies?dryRun=true")
                    .body(Map.of("dsl", parseDsl(dsl)))
                    .retrieve()
                    .toEntity(Map.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Dry-run validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Save a new strategy (strategy-service API).
     */
    public boolean saveStrategy(String dsl) {
        try {
            Map<String, Object> dslMap = parseDsl(dsl);

            // Ensure source is LLM
            dslMap.put("source", "LLM");

            ResponseEntity<Map> response = restClient.post()
                    .uri(strategyServiceUrl + "/api/strategies")
                    .body(dslMap)
                    .retrieve()
                    .toEntity(Map.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to save strategy: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Deactivate a strategy (strategy-service API).
     */
    public boolean deactivate(UUID strategyId) {
        try {
            // First try PATCH via strategy-service
            restClient.patch()
                    .uri(strategyServiceUrl + "/api/strategies/" + strategyId)
                    .body(Map.of("isActive", false))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Strategy-service PATCH failed, updating local DB directly: {}", e.getMessage());

            // Fallback: update local database directly
            String sql = "UPDATE strategies SET is_active = false, updated_at = now() WHERE id = ?";
            int rows = jdbcTemplate.update(sql, strategyId);
            return rows > 0;
        }
    }

    /**
     * Parse DSL string to Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDsl(String dsl) {
        try {
            return objectMapper.readValue(dsl, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid DSL JSON", e);
        }
    }

    /**
     * RowMapper for StrategyRecord.
     */
    private static class StrategyRowMapper implements RowMapper<StrategyRecord> {
        @Override
        public StrategyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = (Map<String, Object>) rs.getObject("dsl");

            return StrategyRecord.builder()
                    .id((UUID) rs.getObject("id"))
                    .name(rs.getString("name"))
                    .source(rs.getString("source"))
                    .dsl(dsl)
                    .isActive(rs.getBoolean("is_active"))
                    .build();
        }
    }
}
