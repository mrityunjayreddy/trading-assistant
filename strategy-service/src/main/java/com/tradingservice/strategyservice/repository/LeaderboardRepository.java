package com.tradingservice.strategyservice.repository;

import com.tradingservice.strategyservice.dto.BacktestSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Custom leaderboard query using DISTINCT ON to return the single most recent
 * backtest result per (strategy, symbol) combination, ranked by Sharpe ratio.
 */
@Repository
@RequiredArgsConstructor
public class LeaderboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<BacktestSummary> fetchLeaderboard(int limit) {
        String sql = """
                SELECT * FROM (
                    SELECT DISTINCT ON (br.strategy_id, br.symbol)
                        br.strategy_id,
                        s.name            AS strategy_name,
                        s.source,
                        br.symbol,
                        br.interval,
                        br.total_trades,
                        br.win_rate,
                        br.total_pnl,
                        br.max_drawdown,
                        br.sharpe_ratio,
                        br.is_statistically_valid,
                        br.validation_note,
                        br.created_at
                    FROM backtest_results br
                    JOIN strategies s ON s.id = br.strategy_id
                    WHERE s.is_active = true
                    ORDER BY br.strategy_id, br.symbol, br.created_at DESC
                ) latest
                ORDER BY sharpe_ratio DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, rowMapper(), limit);
    }

    private RowMapper<BacktestSummary> rowMapper() {
        return (rs, rowNum) -> new BacktestSummary(
                rs.getObject("strategy_id", UUID.class),
                rs.getString("strategy_name"),
                rs.getString("source"),
                rs.getString("symbol"),
                rs.getString("interval"),
                rs.getInt("total_trades"),
                rs.getDouble("win_rate"),
                rs.getDouble("total_pnl"),
                rs.getDouble("max_drawdown"),
                rs.getDouble("sharpe_ratio"),
                rs.getBoolean("is_statistically_valid"),
                rs.getString("validation_note"),
                rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toInstant() : null
        );
    }
}