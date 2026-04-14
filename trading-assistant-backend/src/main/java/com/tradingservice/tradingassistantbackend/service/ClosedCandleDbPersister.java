package com.tradingservice.tradingassistantbackend.service;

import com.tradingservice.tradingassistantbackend.model.Candle;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Persists a closed {@link Candle} into the {@code market_data} PostgreSQL table.
 * Called from {@link TradingDataService#publishCandle} when {@code candle.closed()} is true.
 *
 * All inserts run on a CompletableFuture.runAsync() thread — the SSE broadcast
 * thread is NEVER blocked by database I/O.
 *
 * ON CONFLICT DO NOTHING: safe to call multiple times for the same candle;
 * the unique constraint (exchange, symbol, interval, open_time) prevents duplicates.
 */
@Component
public class ClosedCandleDbPersister {

    private static final Logger log = LoggerFactory.getLogger(ClosedCandleDbPersister.class);

    private static final String UPSERT_SQL =
        "INSERT INTO market_data " +
        "(exchange, symbol, interval, open_time, close_time, open, high, low, close, volume, trade_count, is_closed) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, true) " +
        "ON CONFLICT (exchange, symbol, interval, open_time) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    public ClosedCandleDbPersister(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Schedules an async DB insert for the given closed candle.
     * Returns immediately — never blocks the caller.
     */
    public void persist(Candle candle) {
        CompletableFuture.runAsync(() -> doInsert(candle))
            .exceptionally(ex -> {
                log.error("Async persist task for candle symbol={} interval={} threw unexpectedly: {}",
                    candle.symbol(), candle.interval(), ex.getMessage(), ex);
                return null;
            });
    }

    private void doInsert(Candle candle) {
        try {
            jdbcTemplate.update(
                UPSERT_SQL,
                "BINANCE",
                candle.symbol(),
                candle.interval(),
                new Timestamp(candle.openTime()),
                new Timestamp(candle.closeTime()),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
            );
            log.debug("Persisted closed candle: symbol={} interval={} openTime={}",
                candle.symbol(), candle.interval(), candle.openTime());
        } catch (DataAccessException e) {
            log.error("DB insert failed for closed candle symbol={} interval={} openTime={}: {}",
                candle.symbol(), candle.interval(), candle.openTime(), e.getMessage());
        }
    }
}