package com.tradingservice.tradingengine.persistence;

import com.tradingservice.tradingengine.dto.SimulationRequest;
import com.tradingservice.tradingengine.model.SimulationResult;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Persists a completed (and enriched) {@link SimulationResult} to the
 * {@code backtest_results} PostgreSQL table via reactive R2DBC.
 *
 * <p>The insert is a fire-and-forget side-effect: if Postgres is unreachable,
 * the error is logged and the original {@link SimulationResult} is returned
 * unchanged so the caller is never affected.</p>
 *
 * <p>This bean is purely additive — it does not modify existing service classes.</p>
 */
@Component
public class BacktestResultPersister {

    private static final Logger log = LoggerFactory.getLogger(BacktestResultPersister.class);

    private static final String INSERT_SQL = """
        INSERT INTO backtest_results
            (id, symbol, interval, from_time, to_time,
             total_trades, win_rate, total_pnl, max_drawdown, sharpe_ratio,
             is_statistically_valid, validation_note, created_at)
        VALUES
            (:id, :symbol, :interval, :fromTime, :toTime,
             :totalTrades, :winRate, :totalPnl, :maxDrawdown, :sharpeRatio,
             :isValid, :validationNote, now())
        """;

    private final DatabaseClient databaseClient;

    public BacktestResultPersister(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Inserts the result into {@code backtest_results} and returns the result
     * (pass-through).  On any error, logs a warning and returns the result
     * unmodified — the simulation response is never blocked by DB availability.
     */
    public Mono<SimulationResult> persist(SimulationResult result, SimulationRequest request) {
        return doInsert(result, request.getSymbol(), request.getInterval(),
                        request.getRange().getStartTime(), request.getRange().getEndTime())
                .thenReturn(result)
                .onErrorResume(e -> {
                    log.warn("Failed to persist backtest result for symbol={} interval={}: {}",
                             request.getSymbol(), request.getInterval(), e.getMessage());
                    return Mono.just(result);
                });
    }

    /**
     * Overload for the DSL controller which passes symbol/interval/range directly.
     */
    public Mono<SimulationResult> persist(SimulationResult result,
                                          String symbol, String interval,
                                          Long fromTime, Long toTime) {
        return doInsert(result, symbol, interval, fromTime, toTime)
                .thenReturn(result)
                .onErrorResume(e -> {
                    log.warn("Failed to persist backtest result for symbol={} interval={}: {}",
                             symbol, interval, e.getMessage());
                    return Mono.just(result);
                });
    }

    // -------------------------------------------------------------------------

    private Mono<Void> doInsert(SimulationResult result,
                                String symbol, String interval,
                                Long fromTime, Long toTime) {
        double totalPnl = result.finalBalance() - result.initialBalance();

        return databaseClient.sql(INSERT_SQL)
                .bind("id",             UUID.randomUUID().toString())
                .bind("symbol",         symbol != null ? symbol.toUpperCase() : "UNKNOWN")
                .bind("interval",       interval != null ? interval : "")
                .bind("fromTime",       fromTime != null ? Instant.ofEpochMilli(fromTime) : null)
                .bind("toTime",         toTime   != null ? Instant.ofEpochMilli(toTime)   : null)
                .bind("totalTrades",    result.tradesCount())
                .bind("winRate",        result.winRate())
                .bind("totalPnl",       totalPnl)
                .bind("maxDrawdown",    result.maxDrawdown())
                .bind("sharpeRatio",    result.annualizedSharpe())
                .bind("isValid",        result.isStatisticallyValid())
                .bind("validationNote", result.validationNote())
                .then();
    }
}