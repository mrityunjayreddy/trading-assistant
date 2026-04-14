package com.tradingservice.aiservice.repository;

import com.tradingservice.aiservice.domain.BacktestResultRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository for backtest_results table operations.
 */
@Repository
public class BacktestResultRepository {

    private final JdbcTemplate jdbcTemplate;

    public BacktestResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Fetch all backtest results ordered by creation time.
     */
    public List<BacktestResultRecord> findAllByOrderByCreatedAtDesc() {
        String sql = """
            SELECT id, strategy_id, symbol, interval, from_time, to_time,
                   total_trades, win_rate, total_pnl, sharpe_ratio, max_drawdown,
                   is_statistically_valid, validation_note, market_context, result_detail, created_at
            FROM backtest_results
            ORDER BY created_at DESC
        """;
        return jdbcTemplate.query(sql, new BacktestResultRowMapper());
    }

    /**
     * Check if a strategy+backtest combination already has a memory entry.
     */
    public boolean hasMemoryEntry(UUID strategyId, Instant createdAt) {
        String sql = """
            SELECT COUNT(*) FROM strategy_memory
            WHERE strategy_id = ? AND created_at > ? - interval '1 hour'
        """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, strategyId, createdAt);
        return count != null && count > 0;
    }

    /**
     * Find strategies to prune based on performance criteria.
     * Returns strategy IDs with totalTrades >= minTrades AND sharpeRatio < maxSharpe.
     */
    public List<UUID> findStrategiesToPrune(int minTrades, double maxSharpe) {
        String sql = """
            SELECT DISTINCT strategy_id
            FROM backtest_results
            WHERE total_trades >= ?
              AND sharpe_ratio < ?
              AND is_statistically_valid = true
            ORDER BY created_at DESC
            LIMIT 10
        """;
        return jdbcTemplate.queryForList(sql, UUID.class, minTrades, maxSharpe);
    }

    /**
     * Get average Sharpe ratio of recent memories (for quality check).
     */
    public Double avgSharpeOfRecentMemories(int limit) {
        String sql = """
            SELECT AVG(sharpe_ratio) FROM (
                SELECT sharpe_ratio FROM strategy_memory
                WHERE sharpe_ratio IS NOT NULL
                ORDER BY created_at DESC
                LIMIT ?
            ) recent
        """;
        return jdbcTemplate.queryForObject(sql, Double.class, limit);
    }

    /**
     * Get a single backtest result by ID.
     */
    public BacktestResultRecord findById(UUID id) {
        String sql = """
            SELECT id, strategy_id, symbol, interval, from_time, to_time,
                   total_trades, win_rate, total_pnl, sharpe_ratio, max_drawdown,
                   is_statistically_valid, validation_note, market_context, result_detail, created_at
            FROM backtest_results
            WHERE id = ?
        """;
        return jdbcTemplate.queryForObject(sql, new BacktestResultRowMapper(), id);
    }

    /**
     * RowMapper for BacktestResultRecord.
     */
    private static class BacktestResultRowMapper implements RowMapper<BacktestResultRecord> {
        @Override
        public BacktestResultRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            @SuppressWarnings("unchecked")
            Map<String, Object> marketContext = (Map<String, Object>) rs.getObject("market_context");
            @SuppressWarnings("unchecked")
            Map<String, Object> resultDetail = (Map<String, Object>) rs.getObject("result_detail");

            return BacktestResultRecord.builder()
                    .id((UUID) rs.getObject("id"))
                    .strategyId((UUID) rs.getObject("strategy_id"))
                    .symbol(rs.getString("symbol"))
                    .interval(rs.getString("interval"))
                    .fromTime(rs.getTimestamp("from_time") != null ? rs.getTimestamp("from_time").toInstant() : null)
                    .toTime(rs.getTimestamp("to_time") != null ? rs.getTimestamp("to_time").toInstant() : null)
                    .totalTrades(rs.getObject("total_trades", Integer.class))
                    .winRate(rs.getObject("win_rate", Double.class))
                    .totalPnl(rs.getObject("total_pnl", Double.class))
                    .sharpeRatio(rs.getObject("sharpe_ratio", Double.class))
                    .maxDrawdown(rs.getObject("max_drawdown", Double.class))
                    .isStatisticallyValid(rs.getBoolean("is_statistically_valid"))
                    .validationNote(rs.getString("validation_note"))
                    .marketContext(marketContext)
                    .resultDetail(resultDetail)
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        }
    }
}
