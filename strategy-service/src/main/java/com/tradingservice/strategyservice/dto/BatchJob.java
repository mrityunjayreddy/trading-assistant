package com.tradingservice.strategyservice.dto;

import java.time.Instant;

/**
 * In-memory state for a running or completed batch backtest job.
 */
public record BatchJob(
        String jobId,
        String status,        // RUNNING | COMPLETED | FAILED
        int totalStrategies,
        int processed,
        int succeeded,
        int failed,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
    public static BatchJob start(String jobId, int total) {
        return new BatchJob(jobId, "RUNNING", total, 0, 0, 0, Instant.now(), null, null);
    }

    public BatchJob withProgress(int processed, int succeeded, int failed) {
        return new BatchJob(jobId, status, totalStrategies, processed, succeeded, failed,
                startedAt, completedAt, errorMessage);
    }

    public BatchJob complete(int succeeded, int failed) {
        return new BatchJob(jobId, "COMPLETED", totalStrategies,
                totalStrategies, succeeded, failed, startedAt, Instant.now(), null);
    }

    public BatchJob fail(String message) {
        return new BatchJob(jobId, "FAILED", totalStrategies,
                processed, succeeded, failed, startedAt, Instant.now(), message);
    }
}