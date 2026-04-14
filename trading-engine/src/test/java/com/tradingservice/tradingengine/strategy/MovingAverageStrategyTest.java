package com.tradingservice.tradingengine.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.indicator.IndicatorService;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.TradeAction;
import com.tradingservice.tradingengine.model.TradeSignal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovingAverageStrategyTest {

    private final IndicatorService indicatorService = new IndicatorService();

    @Test
    void shouldGenerateBuyAndSellSignalsWhenAveragesCross() {
        Strategy strategy = new MovingAverageStrategy(indicatorService, 3, 5);
        List<Kline> klines = List.of(
                kline(1, 10), kline(2, 9), kline(3, 8), kline(4, 7),
                kline(5, 6), kline(6, 7), kline(7, 8), kline(8, 9),
                kline(9, 8), kline(10, 7), kline(11, 6), kline(12, 5)
        );

        List<TradeSignal> signals = strategy.generateSignals(klines);

        assertThat(signals).extracting(TradeSignal::action)
                .contains(TradeAction.BUY, TradeAction.SELL);
    }

    @Test
    void shouldRejectInvalidWindows() {
        assertThatThrownBy(() -> new MovingAverageStrategy(indicatorService, 5, 5))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("shortWindow must be less than longWindow");
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
