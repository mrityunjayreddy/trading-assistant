package com.tradingservice.strategyservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the trading-engine SimulationResult used by the batch backtest.
 * Unknown fields from the engine response are silently ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationResponse(
        double initialBalance,
        double finalBalance,
        double totalReturn,
        int tradesCount,
        Double winRate,
        Double maxDrawdown,
        Double annualizedSharpe,
        Boolean isStatisticallyValid,
        String validationNote
) {}