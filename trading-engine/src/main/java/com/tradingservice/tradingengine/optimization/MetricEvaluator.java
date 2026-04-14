package com.tradingservice.tradingengine.optimization;

import com.tradingservice.tradingengine.model.ExecutedTrade;
import com.tradingservice.tradingengine.model.SimulationResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MetricEvaluator {

    public double evaluate(SimulationResult result, MetricType metric) {
        return switch (metric) {
            case TOTAL_RETURN -> result.totalReturn();
            case SHARPE_RATIO -> calculateSharpeRatio(result);
            case MAX_DRAWDOWN -> -calculateMaxDrawdown(result);
            case WIN_RATE -> calculateWinRate(result);
        };
    }

    public double calculateSharpeRatio(SimulationResult result) {
        List<Double> periodReturns = new ArrayList<>();
        double previousEquity = 0.0;

        for (int index = 0; index < result.equityCurve().size(); index++) {
            double currentEquity = result.equityCurve().get(index).equity();
            if (index > 0 && previousEquity > 0.0) {
                periodReturns.add((currentEquity - previousEquity) / previousEquity);
            }
            previousEquity = currentEquity;
        }

        if (periodReturns.isEmpty()) {
            return 0.0;
        }

        double mean = periodReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = periodReturns.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);
        if (standardDeviation == 0.0) {
            return 0.0;
        }
        return mean / standardDeviation * Math.sqrt(periodReturns.size());
    }

    public double calculateMaxDrawdown(SimulationResult result) {
        double peak = Double.NEGATIVE_INFINITY;
        double maxDrawdown = 0.0;

        for (var point : result.equityCurve()) {
            peak = Math.max(peak, point.equity());
            if (peak > 0.0) {
                double drawdown = ((peak - point.equity()) / peak) * 100.0;
                maxDrawdown = Math.max(maxDrawdown, drawdown);
            }
        }

        return maxDrawdown;
    }

    public double calculateWinRate(SimulationResult result) {
        List<ExecutedTrade> trades = result.trades();
        int closedTrades = 0;
        int winningTrades = 0;

        for (ExecutedTrade trade : trades) {
            if (trade.executionType() == null || !trade.executionType().name().startsWith("CLOSE_")) {
                continue;
            }
            closedTrades++;
            if (trade.realizedPnl() > 0) {
                winningTrades++;
            }
        }

        if (closedTrades == 0) {
            return 0.0;
        }
        return (winningTrades * 100.0) / closedTrades;
    }
}
