package com.tradingservice.tradingengine.optimization;

import java.util.Map;
import lombok.Builder;

@Builder
public record SimulationResultSummary(
        Map<String, Object> params,
        double totalReturn,
        double maxDrawdown,
        int tradesCount,
        double winRate,
        double sharpeRatio,
        double score
) {
}
