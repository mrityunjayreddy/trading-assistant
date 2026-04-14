package com.tradingservice.tradingengine.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.indicator.IndicatorService;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.TradeAction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategyFactoryTest {

    private final StrategyFactory strategyFactory = new DefaultStrategyFactory(new StrategyRegistry(new IndicatorService()));

    @Test
    void shouldCreateRsiStrategyFromRequestParameters() {
        Strategy strategy = strategyFactory.create("RSI", Map.of(
                "period", 3,
                "overbought", 60,
                "oversold", 40
        ));

        List<TradeAction> actions = strategy.generateSignals(List.of(
                kline(1, 10), kline(2, 9), kline(3, 8), kline(4, 7),
                kline(5, 8), kline(6, 9), kline(7, 10), kline(8, 9),
                kline(9, 8), kline(10, 7)
        )).stream().map(signal -> signal.action()).toList();

        assertThat(actions).contains(TradeAction.BUY, TradeAction.SELL);
    }

    @Test
    void shouldCreateBollingerBandsStrategyFromRequestParameters() {
        Strategy strategy = strategyFactory.create("BOLLINGER_BANDS", Map.of(
                "window", 3,
                "stdDevMultiplier", 1.0
        ));

        List<TradeAction> actions = strategy.generateSignals(List.of(
                kline(1, 10), kline(2, 10), kline(3, 10), kline(4, 7),
                kline(5, 11), kline(6, 13), kline(7, 10), kline(8, 9)
        )).stream().map(signal -> signal.action()).toList();

        assertThat(actions).contains(TradeAction.BUY, TradeAction.SELL);
    }

    @Test
    void shouldRejectUnknownStrategyName() {
        assertThatThrownBy(() -> strategyFactory.create("NOT_REAL", Map.of()))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("Unsupported strategy");
    }

    private Kline kline(long openTime, double close) {
        return Kline.builder()
                .openTime(openTime)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(100)
                .build();
    }
}
