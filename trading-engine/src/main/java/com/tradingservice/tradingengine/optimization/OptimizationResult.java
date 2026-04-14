package com.tradingservice.tradingengine.optimization;

import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record OptimizationResult(
        Map<String, Object> bestParams,
        double bestScore,
        MetricType metricUsed,
        int evaluatedCombinations,
        int successfulCombinations,
        List<SimulationResultSummary> topResults
) {
}
