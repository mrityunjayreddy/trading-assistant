package com.tradingservice.aiservice.memory;

import com.tradingservice.aiservice.domain.MarketContext;
import com.tradingservice.aiservice.domain.MemoryRecord;
import com.tradingservice.aiservice.embedding.FeatureVectorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Retrieves relevant memories from strategy_memory using pgvector similarity search.
 *
 * Retrieval process:
 * 1. Build query vector from market context (segments 2 + 3 only)
 * 2. SQL: cosine similarity search with 180-day filter, LIMIT 20
 * 3. Re-rank top 20 by composite score:
 *    score = (0.35 * similarity) + (0.45 * normalized_sharpe) + (0.20 * (1 - normalized_drawdown))
 * 4. Return top {topK} after re-ranking, filtered: verdict != null
 */
@Component
public class StrategyMemoryReader {

    private static final Logger log = LoggerFactory.getLogger(StrategyMemoryReader.class);

    private final JdbcTemplate jdbcTemplate;
    private final FeatureVectorBuilder featureVectorBuilder;

    public StrategyMemoryReader(JdbcTemplate jdbcTemplate, FeatureVectorBuilder featureVectorBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.featureVectorBuilder = featureVectorBuilder;
    }

    /**
     * Retrieve top K most relevant memories for the given market context.
     */
    public List<MemoryRecord> retrieve(MarketContext context, int topK) {
        log.debug("Retrieving top {} memories for context: {} {} (trend={}, vol={})",
                topK, context.getSymbol(), context.getInterval(),
                context.getTrend(), context.getVolatility());

        // Build query vector from context (segments 2 and 3 only)
        float[] queryVector = featureVectorBuilder.buildQueryVector(context, null, null);
        String vectorString = vectorToString(queryVector);

        // SQL: cosine similarity search with pgvector
        // Using <=> for cosine distance (1 - similarity)
        String sql = """
            SELECT
                id, strategy_name, strategy_id, document, sharpe_ratio, win_rate,
                max_drawdown, trade_count, verdict, market_context, created_at,
                1 - (embedding <=> ?::vector) AS similarity
            FROM strategy_memory
            WHERE created_at > now() - interval '180 days'
              AND verdict IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT 20
        """;

        List<MemoryRecord> results = jdbcTemplate.query(sql, new MemoryRowMapper(), vectorString, vectorString);
        log.debug("Raw similarity search returned {} results", results.size());

        // Re-rank by composite score
        for (MemoryRecord record : results) {
            double normalizedSharpe = normalizeSharpe(record.getSharpeRatio());
            double normalizedDrawdown = record.getMaxDrawdown() != null ? record.getMaxDrawdown() / 100.0 : 0.5;
            double similarity = record.getSimilarityScore() != null ? record.getSimilarityScore() : 0.0;

            // Composite score: 0.35 * similarity + 0.45 * normalized_sharpe + 0.20 * (1 - normalized_drawdown)
            double compositeScore = (0.35 * similarity)
                                  + (0.45 * normalizedSharpe)
                                  + (0.20 * (1.0 - normalizedDrawdown));

            record.setCompositeScore(compositeScore);
            log.trace("Memory {} composite={} sim={} sharpe={} dd={}",
                    record.getId(),
                    String.format("%.4f", compositeScore), String.format("%.4f", similarity),
                    String.format("%.4f", normalizedSharpe), String.format("%.4f", normalizedDrawdown));
        }

        // Sort by composite score descending
        results.sort((a, b) -> Double.compare(b.getCompositeScore(), a.getCompositeScore()));

        // Return top K
        List<MemoryRecord> topResults = results.stream()
                .limit(topK)
                .toList();

        log.info("Retrieved {} memories (re-ranked from {})", topResults.size(), results.size());
        return topResults;
    }

    /**
     * Normalize Sharpe ratio to 0-1 range.
     * Maps -3..3 to 0..1
     */
    private double normalizeSharpe(Double sharpe) {
        if (sharpe == null) return 0.5;
        return (sharpe + 3.0) / 6.0;
    }

    /**
     * Convert float[] to PostgreSQL vector string format.
     */
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "%.6f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * RowMapper for MemoryRecord.
     */
    private static class MemoryRowMapper implements RowMapper<MemoryRecord> {
        @Override
        public MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return MemoryRecord.builder()
                    .id((UUID) rs.getObject("id"))
                    .strategyName(rs.getString("strategy_name"))
                    .strategyId((UUID) rs.getObject("strategy_id"))
                    .document(rs.getString("document"))
                    .sharpeRatio(rs.getObject("sharpe_ratio", Double.class))
                    .winRate(rs.getObject("win_rate", Double.class))
                    .maxDrawdown(rs.getObject("max_drawdown", Double.class))
                    .tradeCount(rs.getObject("trade_count", Integer.class))
                    .verdict(rs.getString("verdict"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .similarityScore(rs.getObject("similarity", Double.class))
                    .build();
        }
    }
}
