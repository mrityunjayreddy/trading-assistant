package com.tradingservice.tradingengine.indicator;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.model.Kline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndicatorService {

    public List<Double> calculateMovingAverage(List<Kline> klines, int window) {
        validateWindow(window, "Moving average");

        if (klines.isEmpty()) {
            return List.of();
        }

        List<Double> values = new ArrayList<>(Collections.nCopies(klines.size(), Double.NaN));
        double rollingSum = 0.0;

        for (int index = 0; index < klines.size(); index++) {
            rollingSum += klines.get(index).close();
            if (index >= window) {
                rollingSum -= klines.get(index - window).close();
            }
            if (index + 1 >= window) {
                values.set(index, rollingSum / window);
            }
        }

        return values;
    }

    public List<Double> calculateRsi(List<Kline> klines, int period) {
        validateWindow(period, "RSI");

        if (klines.isEmpty()) {
            return List.of();
        }

        List<Double> rsi = new ArrayList<>(Collections.nCopies(klines.size(), Double.NaN));
        if (klines.size() <= period) {
            return rsi;
        }

        double gains = 0.0;
        double losses = 0.0;

        for (int index = 1; index <= period; index++) {
            double delta = klines.get(index).close() - klines.get(index - 1).close();
            gains += Math.max(delta, 0.0);
            losses += Math.max(-delta, 0.0);
        }

        double averageGain = gains / period;
        double averageLoss = losses / period;
        rsi.set(period, toRsi(averageGain, averageLoss));

        for (int index = period + 1; index < klines.size(); index++) {
            double delta = klines.get(index).close() - klines.get(index - 1).close();
            double gain = Math.max(delta, 0.0);
            double loss = Math.max(-delta, 0.0);
            averageGain = ((averageGain * (period - 1)) + gain) / period;
            averageLoss = ((averageLoss * (period - 1)) + loss) / period;
            rsi.set(index, toRsi(averageGain, averageLoss));
        }

        return rsi;
    }

    public List<BollingerBands> calculateBollingerBands(List<Kline> klines, int window, double stdDevMultiplier) {
        validateWindow(window, "Bollinger bands");
        if (stdDevMultiplier <= 0.0) {
            throw new InvalidStrategyConfigurationException("Bollinger bands standard deviation multiplier must be greater than zero");
        }

        if (klines.isEmpty()) {
            return List.of();
        }

        List<BollingerBands> bands = new ArrayList<>(Collections.nCopies(klines.size(), null));
        double rollingSum = 0.0;
        double rollingSquareSum = 0.0;

        for (int index = 0; index < klines.size(); index++) {
            double close = klines.get(index).close();
            rollingSum += close;
            rollingSquareSum += close * close;

            if (index >= window) {
                double exitingClose = klines.get(index - window).close();
                rollingSum -= exitingClose;
                rollingSquareSum -= exitingClose * exitingClose;
            }

            if (index + 1 >= window) {
                double mean = rollingSum / window;
                double variance = Math.max((rollingSquareSum / window) - (mean * mean), 0.0);
                double standardDeviation = Math.sqrt(variance);
                bands.set(index, new BollingerBands(
                        mean - (standardDeviation * stdDevMultiplier),
                        mean,
                        mean + (standardDeviation * stdDevMultiplier)
                ));
            }
        }

        return bands;
    }

    private double toRsi(double averageGain, double averageLoss) {
        if (averageLoss == 0.0) {
            return 100.0;
        }
        double relativeStrength = averageGain / averageLoss;
        return 100.0 - (100.0 / (1.0 + relativeStrength));
    }

    private void validateWindow(int window, String indicatorName) {
        if (window <= 0) {
            throw new InvalidStrategyConfigurationException(indicatorName + " window must be greater than zero");
        }
    }
}
