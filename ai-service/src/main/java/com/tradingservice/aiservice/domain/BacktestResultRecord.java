package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a backtest result from backtest_results table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultRecord {

    private UUID id;
    private UUID strategyId;
    private String symbol;
    private String interval;
    private Instant fromTime;
    private Instant toTime;
    private Integer totalTrades;
    private Double winRate;
    private Double totalPnl;
    private Double sharpeRatio;
    private Double maxDrawdown;
    private Boolean isStatisticallyValid;
    private String validationNote;
    private Map<String, Object> marketContext;
    private Map<String, Object> resultDetail;
    private Instant createdAt;
}
