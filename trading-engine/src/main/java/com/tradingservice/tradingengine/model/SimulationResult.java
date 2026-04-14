package com.tradingservice.tradingengine.model;

import java.util.List;
import lombok.Builder;

@Builder
public record SimulationResult(
        // ---- original fields (unchanged) ----
        double initialBalance,
        double finalBalance,
        double totalReturn,
        int tradesCount,
        List<Kline> candles,
        List<ExecutedTrade> trades,
        List<EquityPoint> equityCurve,

        // ---- enrichment fields (nullable — absent on older callers) ----
        Double winRate,
        Double maxDrawdown,
        Double annualizedSharpe,
        Boolean isStatisticallyValid,
        String validationNote
) {
}
