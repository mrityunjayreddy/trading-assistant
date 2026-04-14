package com.tradingservice.tradingengine.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradingservice.tradingengine.model.EquityPoint;
import com.tradingservice.tradingengine.model.ExecutedTrade;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.PositionSide;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.model.TradeAction;
import com.tradingservice.tradingengine.model.TradeExecutionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricEvaluatorTest {

    private final MetricEvaluator metricEvaluator = new MetricEvaluator();

    @Test
    void shouldCalculateOptimizationMetrics() {
        SimulationResult result = SimulationResult.builder()
                .initialBalance(1000.0)
                .finalBalance(1120.0)
                .totalReturn(12.0)
                .tradesCount(2)
                .candles(List.of(
                        candle(1, 100),
                        candle(2, 95),
                        candle(3, 105)
                ))
                .trades(List.of(
                        trade(1, TradeAction.BUY, 100, 10, 1000),
                        trade(3, TradeAction.SELL, 112, 10, 1120)
                ))
                .equityCurve(List.of(
                        equity(1, 1000),
                        equity(2, 950),
                        equity(3, 1120)
                ))
                .build();

        assertThat(metricEvaluator.evaluate(result, MetricType.TOTAL_RETURN)).isEqualTo(12.0);
        assertThat(metricEvaluator.calculateMaxDrawdown(result)).isEqualTo(5.0);
        assertThat(metricEvaluator.calculateWinRate(result)).isEqualTo(100.0);
        assertThat(metricEvaluator.evaluate(result, MetricType.MAX_DRAWDOWN)).isEqualTo(-5.0);
        assertThat(metricEvaluator.calculateSharpeRatio(result)).isGreaterThan(0.0);
    }

    private Kline candle(long time, double close) {
        return Kline.builder()
                .openTime(time)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(10)
                .build();
    }

    private ExecutedTrade trade(long timestamp, TradeAction action, double price, double quantity, double notional) {
        return ExecutedTrade.builder()
                .timestamp(timestamp)
                .action(action)
                .positionSide(PositionSide.LONG)
                .executionType(action == TradeAction.BUY ? TradeExecutionType.OPEN_LONG : TradeExecutionType.CLOSE_LONG)
                .price(price)
                .quantity(quantity)
                .notional(notional)
                .realizedPnl(action == TradeAction.SELL ? 120 : 0)
                .cashBalanceAfter(0)
                .assetQuantityAfter(0)
                .build();
    }

    private EquityPoint equity(long timestamp, double value) {
        return EquityPoint.builder()
                .timestamp(timestamp)
                .equity(value)
                .closePrice(value)
                .build();
    }
}
