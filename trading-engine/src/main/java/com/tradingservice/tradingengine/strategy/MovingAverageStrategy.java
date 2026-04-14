package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.indicator.IndicatorService;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.TradeAction;
import com.tradingservice.tradingengine.model.TradeSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MovingAverageStrategy implements Strategy {

    public static final String NAME = "MA_CROSSOVER";
    private static final int DEFAULT_SHORT_WINDOW = 10;
    private static final int DEFAULT_LONG_WINDOW = 50;

    private final IndicatorService indicatorService;
    private final int shortWindow;
    private final int longWindow;

    public MovingAverageStrategy(IndicatorService indicatorService, int shortWindow, int longWindow) {
        this.indicatorService = indicatorService;
        this.shortWindow = shortWindow;
        this.longWindow = longWindow;
        validate();
    }

    @Override
    public List<TradeSignal> generateSignals(List<Kline> klines) {
        if (klines.size() < longWindow) {
            throw new InvalidStrategyConfigurationException(
                    "Insufficient kline data for MA_CROSSOVER. Required at least " + longWindow + " data points"
            );
        }

        List<Double> shortMovingAverage = indicatorService.calculateMovingAverage(klines, shortWindow);
        List<Double> longMovingAverage = indicatorService.calculateMovingAverage(klines, longWindow);
        List<TradeSignal> signals = new ArrayList<>(klines.size());

        for (int index = 0; index < klines.size(); index++) {
            Kline kline = klines.get(index);
            double currentShortAverage = shortMovingAverage.get(index);
            double currentLongAverage = longMovingAverage.get(index);

            if (Double.isNaN(currentShortAverage) || Double.isNaN(currentLongAverage) || index == 0) {
                signals.add(signal(kline, TradeAction.HOLD));
                continue;
            }

            double previousShortAverage = shortMovingAverage.get(index - 1);
            double previousLongAverage = longMovingAverage.get(index - 1);
            if (Double.isNaN(previousShortAverage) || Double.isNaN(previousLongAverage)) {
                signals.add(signal(kline, TradeAction.HOLD));
                continue;
            }

            TradeAction action = TradeAction.HOLD;
            if (previousShortAverage <= previousLongAverage && currentShortAverage > currentLongAverage) {
                action = TradeAction.BUY;
            } else if (previousShortAverage >= previousLongAverage && currentShortAverage < currentLongAverage) {
                action = TradeAction.SELL;
            }

            signals.add(signal(kline, action));
        }

        log.info("Generated {} signals using {} shortWindow={} longWindow={}",
                signals.size(), NAME, shortWindow, longWindow);
        return signals;
    }

    public static MovingAverageStrategy from(Map<String, Object> parameters, IndicatorService indicatorService) {
        StrategyParameterReader reader = new StrategyParameterReader(parameters);
        return new MovingAverageStrategy(
                indicatorService,
                reader.getInt("shortWindow", DEFAULT_SHORT_WINDOW),
                reader.getInt("longWindow", DEFAULT_LONG_WINDOW)
        );
    }

    private void validate() {
        if (shortWindow <= 0 || longWindow <= 0) {
            throw new InvalidStrategyConfigurationException("Moving average windows must be greater than zero");
        }
        if (shortWindow >= longWindow) {
            throw new InvalidStrategyConfigurationException("shortWindow must be less than longWindow");
        }
    }

    private TradeSignal signal(Kline kline, TradeAction action) {
        return TradeSignal.builder()
                .timestamp(kline.openTime())
                .action(action)
                .price(kline.close())
                .build();
    }
}
