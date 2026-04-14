package com.tradingservice.aiservice.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import com.tradingservice.aiservice.domain.*;
import com.tradingservice.aiservice.embedding.FeatureVectorBuilder;
import com.tradingservice.aiservice.service.AnthropicClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/**
 * Writes backtest results to strategy_memory with strict quality gates.
 *
 * MEMORY QUALITY GATES - ALL must pass for storage:
 * 1. isStatisticallyValid = true (>= 50 trades)
 * 2. maxDrawdown < 30.0%
 * 3. totalTrades >= 50 (belt-and-suspenders check)
 * 4. Backtest covered >= 120 days of data
 * 5. sharpeRatio > -1.0 (filter disasters, keep poor results as learning)
 *
 * If ANY gate fails: log which gate failed and the values. Do not store. Do not retry.
 */
@Component
public class StrategyMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(StrategyMemoryWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final FeatureVectorBuilder featureVectorBuilder;
    private final AnthropicClient anthropicClient;
    private final LessonCache lessonCache;
    private final ObjectMapper objectMapper;

    public StrategyMemoryWriter(
            JdbcTemplate jdbcTemplate,
            FeatureVectorBuilder featureVectorBuilder,
            AnthropicClient anthropicClient,
            LessonCache lessonCache,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.featureVectorBuilder = featureVectorBuilder;
        this.anthropicClient = anthropicClient;
        this.lessonCache = lessonCache;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Register PGvector type with JDBC — addVectorType requires a Connection
        javax.sql.DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) {
            log.warn("DataSource is null — cannot register PGvector type");
            return;
        }
        Connection conn = DataSourceUtils.getConnection(ds);
        try {
            PGvector.addVectorType(conn);
            log.debug("PGvector type registered");
        } catch (Exception e) {
            log.warn("Could not register PGvector type (may already be registered): {}", e.getMessage());
        } finally {
            DataSourceUtils.releaseConnection(conn, ds);
        }
    }

    /**
     * Attempt to store a backtest result in strategy_memory.
     * Returns StorageResult indicating success or rejection reason.
     */
    @Transactional
    public StorageResult store(StrategyRecord strategy, BacktestResultRecord result, MarketContext context) {
        String strategyName = strategy.getName();
        UUID strategyId = strategy.getId();

        log.debug("Evaluating memory storage for strategy: {} (id={})", strategyName, strategyId);

        // Run all 5 quality gates
        QualityGateResult gateResult = runQualityGates(result);
        if (!gateResult.passed()) {
            log.info("REJECTED memory store for {}: {} - {}",
                    strategyName, gateResult.failedGate(), gateResult.details());
            return StorageResult.rejected(gateResult.details());
        }

        log.debug("All quality gates passed for {}", strategyName);

        // Build memory document
        String verdict = determineVerdict(result.getSharpeRatio());
        String document = buildMemoryDocument(strategy, result, context, verdict);

        // Generate lesson via Claude API (with caching)
        String lesson = generateLesson(strategy, result, verdict);
        document = document + "\nLine 9: LESSON: " + lesson;

        // Compute embedding vector
        float[] embedding = featureVectorBuilder.buildVector(strategy, result, context);

        // Generate UUID for the memory record
        UUID memoryId = UUID.randomUUID();

        // Insert into strategy_memory table
        String sql = """
            INSERT INTO strategy_memory
            (id, strategy_name, strategy_id, document, embedding, sharpe_ratio, win_rate,
             max_drawdown, trade_count, verdict, market_context, created_at)
            VALUES (?, ?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;

        // Convert float[] to PGvector format
        String vectorString = vectorToString(embedding);

        int rowsInserted = jdbcTemplate.update(sql,
                memoryId,
                strategyName,
                strategyId,
                document,
                vectorString,
                result.getSharpeRatio(),
                result.getWinRate(),
                result.getMaxDrawdown(),
                result.getTotalTrades(),
                verdict,
                toJson(context),
                Instant.now()
        );

        if (rowsInserted > 0) {
            log.info("STORED memory for {} (id={}, sharpe={}, verdict={})",
                    strategyName, memoryId,
                    result.getSharpeRatio() != null ? String.format("%.3f", result.getSharpeRatio()) : "null",
                    verdict);
            return StorageResult.stored(memoryId);
        } else {
            log.error("Failed to insert memory record for {}", strategyName);
            return StorageResult.rejected("Database insert failed");
        }
    }

    /**
     * Run all 5 quality gates. Returns result with first failing gate.
     */
    private QualityGateResult runQualityGates(BacktestResultRecord result) {
        // Gate 1: isStatisticallyValid = true
        if (result.getIsStatisticallyValid() == null || !result.getIsStatisticallyValid()) {
            return QualityGateResult.failed(
                "GATE_1_STATISTICAL_VALIDITY",
                String.format("isStatisticallyValid=%s (requires true, meaning >= 50 trades)",
                    result.getIsStatisticallyValid())
            );
        }

        // Gate 2: maxDrawdown < 30.0%
        if (result.getMaxDrawdown() == null || result.getMaxDrawdown() >= 30.0) {
            return QualityGateResult.failed(
                "GATE_2_MAX_DRAWDOWN",
                String.format("maxDrawdown=%.2f%% (requires < 30.0%%)",
                    result.getMaxDrawdown() != null ? result.getMaxDrawdown() : 0.0)
            );
        }

        // Gate 3: totalTrades >= 50 (belt-and-suspenders)
        if (result.getTotalTrades() == null || result.getTotalTrades() < 50) {
            return QualityGateResult.failed(
                "GATE_3_MIN_TRADES",
                String.format("totalTrades=%d (requires >= 50)",
                    result.getTotalTrades() != null ? result.getTotalTrades() : 0)
            );
        }

        // Gate 4: Backtest covered >= 120 days
        if (result.getFromTime() != null && result.getToTime() != null) {
            long daysCovered = result.getFromTime().until(result.getToTime(), java.time.temporal.ChronoUnit.DAYS);
            if (daysCovered < 120) {
                return QualityGateResult.failed(
                    "GATE_4_MIN_DATA_DURATION",
                    String.format("backtestDuration=%d days (requires >= 120 days)", daysCovered)
                );
            }
        } else {
            log.warn("Cannot verify data duration - fromTime or toTime is null");
        }

        // Gate 5: sharpeRatio > -1.0
        if (result.getSharpeRatio() == null || result.getSharpeRatio() <= -1.0) {
            return QualityGateResult.failed(
                "GATE_5_MIN_SHARPE",
                String.format("sharpeRatio=%.3f (requires > -1.0)",
                    result.getSharpeRatio() != null ? result.getSharpeRatio() : -999.0)
            );
        }

        return QualityGateResult.ok();
    }

    /**
     * Determine verdict based on Sharpe ratio.
     */
    private String determineVerdict(Double sharpeRatio) {
        if (sharpeRatio == null) return "POOR";
        if (sharpeRatio > 1.5) return "STRONG";
        if (sharpeRatio > 0.5) return "ACCEPTABLE";
        return "POOR";
    }

    /**
     * Build the memory document (8 lines of structured text).
     */
    private String buildMemoryDocument(StrategyRecord strategy, BacktestResultRecord result,
                                        MarketContext context, String verdict) {
        StringBuilder doc = new StringBuilder();

        // Line 1: STRATEGY: {name} | SOURCE: {source}
        doc.append("Line 1: STRATEGY: ")
           .append(strategy.getName())
           .append(" | SOURCE: ")
           .append(strategy.getSource())
           .append("\n");

        // Line 2: ENTRY: {entry} | EXIT: {exit}
        doc.append("Line 2: ENTRY: ")
           .append(strategy.getEntry())
           .append(" | EXIT: ")
           .append(strategy.getExit())
           .append("\n");

        // Line 3: INDICATORS: {comma list of id:type:period}
        doc.append("Line 3: INDICATORS: ");
        List<String> indicatorList = new ArrayList<>();
        for (Map<String, Object> ind : strategy.getIndicators()) {
            String id = (String) ind.get("id");
            String type = (String) ind.get("type");
            Map<String, Object> params = (Map<String, Object>) ind.get("params");
            Integer period = params != null && params.containsKey("period")
                ? ((Number) params.get("period")).intValue() : null;
            indicatorList.add(id + ":" + type + ":" + (period != null ? period : "N/A"));
        }
        doc.append(String.join(", ", indicatorList)).append("\n");

        // Line 4: RISK: SL={sl}% TP={tp}% SIZE={size}%
        Map<String, Object> risk = strategy.getRisk();
        doc.append("Line 4: RISK: SL=")
           .append(risk.getOrDefault("stopLossPct", "N/A"))
           .append("% TP=")
           .append(risk.getOrDefault("takeProfitPct", "N/A"))
           .append("% SIZE=")
           .append(risk.getOrDefault("positionSizePct", "N/A"))
           .append("%\n");

        // Line 5: MARKET: symbol={sym} interval={int} from={from} to={to}
        doc.append("Line 5: MARKET: symbol=")
           .append(result.getSymbol())
           .append(" interval=")
           .append(result.getInterval())
           .append(" from=")
           .append(result.getFromTime())
           .append(" to=")
           .append(result.getToTime())
           .append("\n");

        // Line 6: REGIME: trend={trend} volatility={vol} session={session}
        if (context != null) {
            doc.append("Line 6: REGIME: trend=")
               .append(context.getTrend())
               .append(" volatility=")
               .append(context.getVolatility())
               .append(" session=")
               .append(context.getSession())
               .append("\n");
        } else {
            doc.append("Line 6: REGIME: unknown\n");
        }

        // Line 7: RESULTS: trades={n} winRate={wr}% sharpe={s} maxDD={dd}% pnl=${pnl}
        doc.append("Line 7: RESULTS: trades=")
           .append(result.getTotalTrades())
           .append(" winRate=")
           .append(String.format("%.1f", result.getWinRate() != null ? result.getWinRate() : 0.0))
           .append("% sharpe=")
           .append(String.format("%.3f", result.getSharpeRatio() != null ? result.getSharpeRatio() : 0.0))
           .append(" maxDD=")
           .append(String.format("%.2f", result.getMaxDrawdown() != null ? result.getMaxDrawdown() : 0.0))
           .append("% pnl=$")
           .append(String.format("%.2f", result.getTotalPnl() != null ? result.getTotalPnl() : 0.0))
           .append("\n");

        // Line 8: VERDICT
        doc.append("Line 8: VERDICT: ").append(verdict);

        return doc.toString();
    }

    /**
     * Generate lesson via Claude API with caching.
     */
    private String generateLesson(StrategyRecord strategy, BacktestResultRecord result, String verdict) {
        // Check cache first: same strategy_id + same verdict
        String cacheKey = strategy.getId() + "_" + verdict;
        String cachedLesson = lessonCache.get(cacheKey);
        if (cachedLesson != null) {
            log.debug("Cache hit for lesson: {}", cacheKey);
            return cachedLesson;
        }

        // Build Claude prompt
        String prompt = String.format(
            "In one sentence (max 15 words), what does this trading result teach us? " +
            "Strategy: %s. Entry: %s. Sharpe: %.3f. Win rate: %.1f%%. " +
            "Max drawdown: %.2f%%. Result: %s.",
            strategy.getName(),
            truncate(strategy.getEntry(), 30),
            result.getSharpeRatio() != null ? result.getSharpeRatio() : 0.0,
            result.getWinRate() != null ? result.getWinRate() : 0.0,
            result.getMaxDrawdown() != null ? result.getMaxDrawdown() : 0.0,
            verdict
        );

        try {
            String lesson = anthropicClient.generateLesson(prompt);
            if (lesson != null && !lesson.isEmpty()) {
                lessonCache.put(cacheKey, lesson);
                log.debug("Generated lesson for {} ({}): {}", strategy.getName(), verdict, lesson);
                return lesson;
            }
        } catch (Exception e) {
            log.warn("Claude API failed for lesson generation, using fallback: {}", e.getMessage());
        }

        // Fallback lessons based on verdict
        String fallbackLesson = switch (verdict) {
            case "STRONG" -> "Effective trend-following approach with controlled drawdown";
            case "ACCEPTABLE" -> "Moderate performance — consider tightening stop-loss";
            default -> "Underperformed — entry conditions too broad or exit too late";
        };

        log.debug("Using fallback lesson for {}: {}", strategy.getName(), verdict);
        return fallbackLesson;
    }

    /**
     * Convert float[] to PostgreSQL vector string format.
     */
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US, "%.6f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert object to JSON string for JSONB column.
     */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }

    /**
     * Simple in-memory lesson cache.
     */
    @Component
    public static class LessonCache {
        private final Map<String, String> cache = new HashMap<>();
        private static final int MAX_SIZE = 1000;

        public String get(String key) {
            return cache.get(key);
        }

        public void put(String key, String value) {
            if (cache.size() >= MAX_SIZE) {
                // Collect keys to a list first to avoid ConcurrentModificationException
                List<String> toEvict = new ArrayList<>(cache.keySet()).stream()
                        .limit(MAX_SIZE / 10)
                        .toList();
                toEvict.forEach(cache::remove);
            }
            cache.put(key, value);
        }
    }

    /**
     * Result of quality gate evaluation.
     */
    private record QualityGateResult(boolean passed, String failedGate, String details) {
        static QualityGateResult ok() {
            return new QualityGateResult(true, null, null);
        }

        static QualityGateResult failed(String gate, String details) {
            return new QualityGateResult(false, gate, details);
        }
    }
}
