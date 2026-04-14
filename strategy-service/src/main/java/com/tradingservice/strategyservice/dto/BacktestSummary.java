package com.tradingservice.strategyservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary row returned from the leaderboard query.
 */
public record BacktestSummary(
        UUID strategyId,
        String strategyName,
        String source,
        String symbol,
        String interval,
        int totalTrades,
        double winRate,
        double totalPnl,
        double maxDrawdown,
        double sharpeRatio,
        boolean isStatisticallyValid,
        String validationNote,
        Instant createdAt
) {}