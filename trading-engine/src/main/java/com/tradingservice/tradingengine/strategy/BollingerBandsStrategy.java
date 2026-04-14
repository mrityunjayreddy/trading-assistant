package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.indicator.BollingerBands;
import com.tradingservice.tradingengine.indicator.IndicatorService;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.TradeAction;
import com.tradingservice.tradingengine.model.TradeSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BollingerBandsStrategy implements Strategy {

    public static final String NAME = "BOLLINGER_BANDS";
    private static final int DEFAULT_WINDOW = 20;
    private static final double DEFAULT_STD_DEV_MULTIPLIER = 2.0;

    private final IndicatorService indicatorService;
    private final int window;
    private final double stdDevMultiplier;

    public BollingerBandsStrategy(IndicatorService indicatorService, int window, double stdDevMultiplier) {
        this.indicatorService = indicatorService;
        this.window = window;
        this.stdDevMultiplier = stdDevMultiplier;
        validate();
    }

    @Override
    public List<TradeSignal> generateSignals(List<Kline> klines) {
        if (klines.size() < window) {
            throw new InvalidStrategyConfigurationException(
                    "Insufficient kline data for BOLLINGER_BANDS. Required at least " + window + " data points"
            );
        }

        List<BollingerBands> bands = indicatorService.calculateBollingerBands(klines, window, stdDevMultiplier);
        List<TradeSignal> signals = new ArrayList<>(klines.size());

        for (int index = 0; index < klines.size(); index++) {
            Kline currentKline = klines.get(index);
            BollingerBands currentBands = bands.get(index);
            if (currentBands == null || index == 0) {
                signals.add(signal(currentKline, TradeAction.HOLD));
                continue;
            }

            BollingerBands previousBands = bands.get(index - 1);
            Kline previousKline = klines.get(index - 1);
            if (previousBands == null) {
                signals.add(signal(currentKline, TradeAction.HOLD));
                continue;
            }

            TradeAction action = TradeAction.HOLD;
            boolean crossedUpFromLowerBand =
                    previousKline.close() <= previousBands.lowerBand() && currentKline.close() > currentBands.lowerBand();
            boolean crossedDownFromUpperBand =
                    previousKline.close() >= previousBands.upperBand() && currentKline.close() < currentBands.upperBand();

            if (crossedUpFromLowerBand) {
                action = TradeAction.BUY;
            } else if (crossedDownFromUpperBand) {
                action = TradeAction.SELL;
            }

            signals.add(signal(currentKline, action));
        }

        log.info("Generated {} signals using {} window={} stdDevMultiplier={}",
                signals.size(), NAME, window, stdDevMultiplier);
        return signals;
    }

    public static BollingerBandsStrategy from(Map<String, Object> parameters, IndicatorService indicatorService) {
        StrategyParameterReader reader = new StrategyParameterReader(parameters);
        return new BollingerBandsStrategy(
                indicatorService,
                reader.getInt("window", DEFAULT_WINDOW),
                reader.getDouble("stdDevMultiplier", DEFAULT_STD_DEV_MULTIPLIER)
        );
    }

    private void validate() {
        if (window <= 1) {
            throw new InvalidStrategyConfigurationException("Bollinger bands window must be greater than one");
        }
        if (stdDevMultiplier <= 0.0) {
            throw new InvalidStrategyConfigurationException("Bollinger bands standard deviation multiplier must be greater than zero");
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
