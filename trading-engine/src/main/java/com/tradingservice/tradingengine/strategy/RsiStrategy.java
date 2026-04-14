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
public class RsiStrategy implements Strategy {

    public static final String NAME = "RSI";
    private static final int DEFAULT_PERIOD = 14;
    private static final double DEFAULT_OVERBOUGHT = 70.0;
    private static final double DEFAULT_OVERSOLD = 30.0;

    private final IndicatorService indicatorService;
    private final int period;
    private final double overbought;
    private final double oversold;

    public RsiStrategy(IndicatorService indicatorService, int period, double overbought, double oversold) {
        this.indicatorService = indicatorService;
        this.period = period;
        this.overbought = overbought;
        this.oversold = oversold;
        validate();
    }

    @Override
    public List<TradeSignal> generateSignals(List<Kline> klines) {
        if (klines.size() <= period) {
            throw new InvalidStrategyConfigurationException(
                    "Insufficient kline data for RSI. Required more than " + period + " data points"
            );
        }

        List<Double> rsiValues = indicatorService.calculateRsi(klines, period);
        List<TradeSignal> signals = new ArrayList<>(klines.size());

        for (int index = 0; index < klines.size(); index++) {
            Kline kline = klines.get(index);
            double currentRsi = rsiValues.get(index);
            if (Double.isNaN(currentRsi) || index == 0) {
                signals.add(signal(kline, TradeAction.HOLD));
                continue;
            }

            double previousRsi = rsiValues.get(index - 1);
            if (Double.isNaN(previousRsi)) {
                signals.add(signal(kline, TradeAction.HOLD));
                continue;
            }

            TradeAction action = TradeAction.HOLD;
            if (previousRsi <= oversold && currentRsi > oversold) {
                action = TradeAction.BUY;
            } else if (previousRsi >= overbought && currentRsi < overbought) {
                action = TradeAction.SELL;
            }
            signals.add(signal(kline, action));
        }

        log.info("Generated {} signals using {} period={} overbought={} oversold={}",
                signals.size(), NAME, period, overbought, oversold);
        return signals;
    }

    public static RsiStrategy from(Map<String, Object> parameters, IndicatorService indicatorService) {
        StrategyParameterReader reader = new StrategyParameterReader(parameters);
        return new RsiStrategy(
                indicatorService,
                reader.getInt("period", DEFAULT_PERIOD),
                reader.getDouble("overbought", DEFAULT_OVERBOUGHT),
                reader.getDouble("oversold", DEFAULT_OVERSOLD)
        );
    }

    private void validate() {
        if (period <= 0) {
            throw new InvalidStrategyConfigurationException("RSI period must be greater than zero");
        }
        if (oversold < 0 || overbought > 100 || oversold >= overbought) {
            throw new InvalidStrategyConfigurationException("RSI thresholds must satisfy 0 <= oversold < overbought <= 100");
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
