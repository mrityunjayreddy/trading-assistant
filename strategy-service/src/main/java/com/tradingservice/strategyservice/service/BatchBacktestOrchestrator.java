package com.tradingservice.strategyservice.service;

import com.tradingservice.strategyservice.dto.BatchJob;
import com.tradingservice.strategyservice.dto.SimulationResponse;
import com.tradingservice.strategyservice.entity.StrategyRecord;
import com.tradingservice.strategyservice.repository.StrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically runs all active strategies through the trading-engine for
 * out-of-sample validation and persists results to {@code backtest_results}.
 *
 * <ul>
 *   <li>Runs every 6 hours via {@code @Scheduled}</li>
 *   <li>Virtual-thread executor for non-blocking concurrent simulations</li>
 *   <li>{@code Semaphore(5)} — at most 5 simulations run concurrently to avoid
 *       overloading the trading-engine</li>
 *   <li>Skips a strategy if a backtest result already exists within the last 6 hours</li>
 *   <li>Job state tracked in {@code jobRegistry} (ConcurrentHashMap) — accessible
 *       via the REST endpoint GET /api/strategies/batch-backtest/{jobId}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchBacktestOrchestrator {

    private static final int  MAX_CONCURRENT = 5;
    private static final long SKIP_IF_RECENT_MS = 6L * 60 * 60 * 1000; // 6 h

    // 90-day validation window
    private static final long WINDOW_MS = 90L * 24 * 60 * 60 * 1000;

    private static final String[] TEST_SYMBOLS    = {"BTCUSDT", "ETHUSDT", "SOLUSDT"};
    private static final String   TEST_INTERVAL   = "1d";

    private static final String INSERT_BACKTEST_SQL = """
            INSERT INTO backtest_results
                (id, strategy_id, symbol, interval, from_time, to_time,
                 total_trades, win_rate, total_pnl, max_drawdown, sharpe_ratio,
                 is_statistically_valid, validation_note, created_at)
            VALUES
                (gen_random_uuid(), ?, ?, ?, ?, ?,
                 ?, ?, ?, ?, ?,
                 ?, ?, now())
            """;

    private static final String RECENT_CHECK_SQL = """
            SELECT COUNT(*) FROM backtest_results
            WHERE strategy_id = ? AND created_at > ?
            """;

    private final StrategyRepository strategyRepository;
    private final RestClient         tradingEngineClient;
    private final JdbcTemplate       jdbcTemplate;

    private final ConcurrentHashMap<String, BatchJob> jobRegistry = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Scheduled trigger
    // -------------------------------------------------------------------------

    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledRun() {
        log.info("BatchBacktestOrchestrator: scheduled run starting");
        triggerAsync();
    }

    // -------------------------------------------------------------------------
    // Manual / programmatic trigger (also used by REST endpoint)
    // -------------------------------------------------------------------------

    public BatchJob triggerAsync() {
        List<StrategyRecord> active = strategyRepository.findByIsActiveTrue(Pageable.unpaged());
        String jobId = UUID.randomUUID().toString();
        BatchJob job = BatchJob.start(jobId, active.size());
        jobRegistry.put(jobId, job);

        CompletableFuture.runAsync(() -> runBatch(jobId, active),
                Executors.newVirtualThreadPerTaskExecutor());

        return job;
    }

    public BatchJob getJob(String jobId) {
        return jobRegistry.get(jobId);
    }

    // -------------------------------------------------------------------------
    // Batch execution
    // -------------------------------------------------------------------------

    private void runBatch(String jobId, List<StrategyRecord> strategies) {
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger failed    = new AtomicInteger(0);
        AtomicInteger processed = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = strategies.stream()
                .map(strategy -> CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            runStrategyBacktest(strategy);
                            succeeded.incrementAndGet();
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failed.incrementAndGet();
                        log.warn("Interrupted while waiting for semaphore for strategy {}",
                                strategy.getId());
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        log.warn("Backtest failed for strategy id={} name='{}': {}",
                                strategy.getId(), strategy.getName(), e.getMessage());
                    } finally {
                        int p = processed.incrementAndGet();
                        jobRegistry.put(jobId, jobRegistry.get(jobId)
                                .withProgress(p, succeeded.get(), failed.get()));
                    }
                }, Executors.newVirtualThreadPerTaskExecutor()))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        BatchJob completed = jobRegistry.get(jobId).complete(succeeded.get(), failed.get());
        jobRegistry.put(jobId, completed);
        log.info("BatchBacktestOrchestrator: job={} completed. succeeded={} failed={}",
                jobId, succeeded.get(), failed.get());
    }

    // -------------------------------------------------------------------------
    // Per-strategy simulation
    // -------------------------------------------------------------------------

    private void runStrategyBacktest(StrategyRecord strategy) {
        Instant recentCutoff = Instant.now().minusMillis(SKIP_IF_RECENT_MS);
        Integer recentCount = jdbcTemplate.queryForObject(
                RECENT_CHECK_SQL, Integer.class, strategy.getId(), recentCutoff);
        if (recentCount != null && recentCount > 0) {
            log.debug("Skipping strategy id={} — recent backtest exists", strategy.getId());
            return;
        }

        long endTime   = Instant.now().toEpochMilli();
        long startTime = endTime - WINDOW_MS;

        for (String symbol : TEST_SYMBOLS) {
            try {
                Map<String, Object> request = Map.of(
                        "symbol",   symbol,
                        "interval", TEST_INTERVAL,
                        "dsl",      strategy.getDsl(),
                        "range",    Map.of("startTime", startTime, "endTime", endTime)
                );

                SimulationResponse result = tradingEngineClient.post()
                        .uri("/api/v1/simulate/dsl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(SimulationResponse.class);

                if (result != null) {
                    persistBacktestResult(strategy.getId(), symbol, result, startTime, endTime);
                }
            } catch (RestClientException e) {
                log.warn("Simulation call failed for strategy={} symbol={}: {}",
                        strategy.getId(), symbol, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void persistBacktestResult(UUID strategyId, String symbol,
                                        SimulationResponse result,
                                        long startTime, long endTime) {
        try {
            jdbcTemplate.update(INSERT_BACKTEST_SQL,
                    strategyId,
                    symbol,
                    TEST_INTERVAL,
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime),
                    result.tradesCount(),
                    result.winRate()         != null ? result.winRate()         : 0.0,
                    result.finalBalance() - result.initialBalance(),
                    result.maxDrawdown()     != null ? result.maxDrawdown()     : 0.0,
                    result.annualizedSharpe() != null ? result.annualizedSharpe() : 0.0,
                    result.isStatisticallyValid() != null && result.isStatisticallyValid(),
                    result.validationNote()
            );
        } catch (Exception e) {
            log.warn("Failed to persist backtest result for strategy={} symbol={}: {}",
                    strategyId, symbol, e.getMessage());
        }
    }
}