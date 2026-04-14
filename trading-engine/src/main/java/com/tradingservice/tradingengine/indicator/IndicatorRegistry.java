package com.tradingservice.tradingengine.indicator;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.PPOIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.averages.DoubleEMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.HMAIndicator;
import org.ta4j.core.indicators.averages.KAMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.TripleEMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.AccumulationDistributionIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.Num;

@Component
public class IndicatorRegistry {

    private final Map<String, IndicatorBuilder> registry;

    public IndicatorRegistry() {
        Map<String, IndicatorBuilder> builders = new LinkedHashMap<>();

        register(builders, "MA", this::buildSma);
        register(builders, "SMA", this::buildSma);
        register(builders, "EMA", (def, input, series, factory, cache) ->
                new EMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "WMA", (def, input, series, factory, cache) ->
                new WMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "DEMA", (def, input, series, factory, cache) ->
                new DoubleEMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "DOUBLE_EMA", (def, input, series, factory, cache) ->
                new DoubleEMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "TEMA", (def, input, series, factory, cache) ->
                new TripleEMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "TRIPLE_EMA", (def, input, series, factory, cache) ->
                new TripleEMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "KAMA", (def, input, series, factory, cache) ->
                new KAMAIndicator(
                        input,
                        IndicatorUtils.getInt(def, "window", 10, "period"),
                        IndicatorUtils.getInt(def, "fastPeriod", 2),
                        IndicatorUtils.getInt(def, "slowPeriod", 30)
                ));
        register(builders, "HMA", (def, input, series, factory, cache) ->
                new HMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));
        register(builders, "HULL_MOVING_AVERAGE", (def, input, series, factory, cache) ->
                new HMAIndicator(input, IndicatorUtils.getInt(def, "window", 14, "barCount", "period")));

        register(builders, "RSI", (def, input, series, factory, cache) ->
                new RSIIndicator(input, IndicatorUtils.getInt(def, "period", 14, "window", "barCount")));
        register(builders, "STOCHASTIC_OSCILLATOR_K", this::buildStochasticK);
        register(builders, "STOCHASTIC_K", this::buildStochasticK);
        register(builders, "STOCHASTIC_OSCILLATOR_D", this::buildStochasticD);
        register(builders, "STOCHASTIC_D", this::buildStochasticD);
        register(builders, "WILLIAMS_R", (def, input, series, factory, cache) ->
                new WilliamsRIndicator(series, IndicatorUtils.getInt(def, "period", 14, "window", "barCount")));
        register(builders, "ROC", (def, input, series, factory, cache) ->
                new ROCIndicator(input, IndicatorUtils.getInt(def, "period", 12, "window", "barCount")));
        register(builders, "MOMENTUM", this::buildMomentum);
        register(builders, "CCI", (def, input, series, factory, cache) ->
                new CCIIndicator(series, IndicatorUtils.getInt(def, "period", 20, "window", "barCount")));

        register(builders, "BOLLINGER", this::buildBollinger);
        register(builders, "BOLLINGER_BANDS", this::buildBollinger);
        register(builders, "ATR", (def, input, series, factory, cache) ->
                new ATRIndicator(series, IndicatorUtils.getInt(def, "period", 14, "window", "barCount")));
        register(builders, "STANDARD_DEVIATION", (def, input, series, factory, cache) ->
                new StandardDeviationIndicator(input, IndicatorUtils.getInt(def, "period", 20, "window", "barCount")));
        register(builders, "STDDEV", (def, input, series, factory, cache) ->
                new StandardDeviationIndicator(input, IndicatorUtils.getInt(def, "period", 20, "window", "barCount")));

        register(builders, "MACD", this::buildMacd);
        register(builders, "PPO", this::buildPpo);

        register(builders, "VWAP", this::buildVwap);
        register(builders, "OBV", (def, input, series, factory, cache) -> new OnBalanceVolumeIndicator(series));
        register(builders, "ON_BALANCE_VOLUME", (def, input, series, factory, cache) -> new OnBalanceVolumeIndicator(series));
        register(builders, "ACCUMULATION_DISTRIBUTION", (def, input, series, factory, cache) -> new AccumulationDistributionIndicator(series));
        register(builders, "AD", (def, input, series, factory, cache) -> new AccumulationDistributionIndicator(series));

        register(builders, "DIFFERENCE", this::buildDifference);
        register(builders, "RATIO", this::buildRatio);
        register(builders, "TRANSFORM", this::buildTransform);
        register(builders, "LAG", this::buildLag);

        this.registry = Map.copyOf(builders);
    }

    public IndicatorBuilder get(String indicatorType) {
        return registry.get(IndicatorUtils.normalizeType(indicatorType));
    }

    private void register(Map<String, IndicatorBuilder> builders, String key, IndicatorBuilder builder) {
        builders.put(IndicatorUtils.normalizeType(key), builder);
    }

    private Indicator<Num> buildSma(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        return new SMAIndicator(input, IndicatorUtils.getInt(definition, "window", 14, "barCount", "period"));
    }

    private Indicator<Num> buildStochasticK(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        int period = IndicatorUtils.getInt(definition, "period", 14, "window", "barCount");
        Indicator<Num> high = factory.resolveInput(
                IndicatorUtils.getObject(definition, "highInput", "highIndicator"),
                series,
                cache,
                "high"
        );
        Indicator<Num> low = factory.resolveInput(
                IndicatorUtils.getObject(definition, "lowInput", "lowIndicator"),
                series,
                cache,
                "low"
        );
        return new StochasticOscillatorKIndicator(input, period, high, low);
    }

    private Indicator<Num> buildStochasticD(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        int period = IndicatorUtils.getInt(definition, "period", 14, "window", "barCount");
        Indicator<Num> high = factory.resolveInput(
                IndicatorUtils.getObject(definition, "highInput", "highIndicator"),
                series,
                cache,
                "high"
        );
        Indicator<Num> low = factory.resolveInput(
                IndicatorUtils.getObject(definition, "lowInput", "lowIndicator"),
                series,
                cache,
                "low"
        );
        StochasticOscillatorKIndicator stochasticK = input instanceof StochasticOscillatorKIndicator existing
                ? existing
                : new StochasticOscillatorKIndicator(input, period, high, low);
        return new StochasticOscillatorDIndicator(stochasticK);
    }

    private Indicator<Num> buildMomentum(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        int period = IndicatorUtils.getInt(definition, "period", 10, "window", "barCount");
        return NumericIndicator.of(input).minus(NumericIndicator.of(input).previous(period));
    }

    private Indicator<Num> buildBollinger(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        int window = IndicatorUtils.getInt(definition, "window", 20, "barCount", "period");
        double multiplier = IndicatorUtils.getDouble(definition, "stdDevMultiplier", 2.0, "multiplier");

        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(new SMAIndicator(input, window));
        StandardDeviationIndicator deviation = new StandardDeviationIndicator(input, window);
        Num multiplierNum = series.numFactory().numOf(multiplier);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, deviation, multiplierNum);
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, deviation, multiplierNum);

        if (IndicatorUtils.hasText(definition.getId())) {
            String normalizedId = IndicatorUtils.normalize(definition.getId());
            cache.put(normalizedId, middle);
            cache.put(normalizedId + ".middle", middle);
            cache.put(normalizedId + ".upper", upper);
            cache.put(normalizedId + ".lower", lower);
        }

        String band = IndicatorUtils.normalizeType(IndicatorUtils.getString(definition, "band", null));
        String subType = IndicatorUtils.normalizeType(definition.getSubType());
        String selection = IndicatorUtils.hasText(subType) ? subType : band;

        return switch (selection) {
            case "UPPER", "BOLLINGER_UPPER", "UPPER_BAND" -> upper;
            case "LOWER", "BOLLINGER_LOWER", "LOWER_BAND" -> lower;
            default -> middle;
        };
    }

    private Indicator<Num> buildMacd(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        int shortPeriod = IndicatorUtils.getInt(definition, "shortPeriod", 12, "fastPeriod");
        int longPeriod = IndicatorUtils.getInt(definition, "longPeriod", 26, "slowPeriod");
        int signalPeriod = IndicatorUtils.getInt(definition, "signalPeriod", 9);

        MACDIndicator macd = new MACDIndicator(input, shortPeriod, longPeriod);
        Indicator<Num> signal = macd.getSignalLine(signalPeriod);
        Indicator<Num> histogram = macd.getHistogram(signalPeriod);

        if (IndicatorUtils.hasText(definition.getId())) {
            String normalizedId = IndicatorUtils.normalize(definition.getId());
            cache.put(normalizedId, macd);
            cache.put(normalizedId + ".signal", signal);
            cache.put(normalizedId + ".histogram", histogram);
        }

        String subType = IndicatorUtils.normalizeType(definition.getSubType());
        return switch (subType) {
            case "SIGNAL", "MACD_SIGNAL" -> signal;
            case "HISTOGRAM", "MACD_HISTOGRAM" -> histogram;
            default -> macd;
        };
    }

    private Indicator<Num> buildPpo(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        return new PPOIndicator(
                input,
                IndicatorUtils.getInt(definition, "shortPeriod", 12, "fastPeriod"),
                IndicatorUtils.getInt(definition, "longPeriod", 26, "slowPeriod")
        );
    }

    private Indicator<Num> buildVwap(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        int window = IndicatorUtils.getInt(definition, "period", 14, "window", "barCount");
        Indicator<Num> volume = factory.resolveInput(
                IndicatorUtils.getObject(definition, "volumeInput", "volumeIndicator"),
                series,
                cache,
                "volume"
        );
        return new VWAPIndicator(input, volume, window);
    }

    private Indicator<Num> buildDifference(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        Object rightInput = IndicatorUtils.getObject(definition, "rightInput", "rightIndicator", "operand");
        if (rightInput != null) {
            Indicator<Num> right = factory.resolveInput(rightInput, series, cache, null);
            return NumericIndicator.of(input).minus(right);
        }

        int lag = IndicatorUtils.getInt(definition, "lag", 1, "offset", "periods");
        return lag == 1 ? new DifferenceIndicator(input) : NumericIndicator.of(input).minus(NumericIndicator.of(input).previous(lag));
    }

    private Indicator<Num> buildRatio(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        Object rightInput = IndicatorUtils.getObject(definition, "rightInput", "rightIndicator", "operand");
        if (rightInput == null) {
            throw IndicatorUtils.invalidParam(definition, "rightInput", "is required for ratio indicators");
        }
        Indicator<Num> right = factory.resolveInput(rightInput, series, cache, null);
        return NumericIndicator.of(input).dividedBy(right);
    }

    private Indicator<Num> buildTransform(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        NumericIndicator numeric = NumericIndicator.of(input);
        String operation = IndicatorUtils.normalizeType(IndicatorUtils.getString(definition, "operation", "ABS", "transform"));

        return switch (operation) {
            case "ABS" -> numeric.abs();
            case "SQRT" -> numeric.sqrt();
            case "SQUARE", "SQUARED" -> numeric.squared();
            case "SMA" -> numeric.sma(IndicatorUtils.requireInt(definition, "window", "period", "barCount"));
            case "EMA" -> numeric.ema(IndicatorUtils.requireInt(definition, "window", "period", "barCount"));
            case "STDDEV", "STANDARD_DEVIATION" -> numeric.stddev(IndicatorUtils.requireInt(definition, "window", "period", "barCount"));
            case "HIGHEST", "MAX" -> numeric.highest(IndicatorUtils.requireInt(definition, "window", "period", "barCount"));
            case "LOWEST", "MIN" -> numeric.lowest(IndicatorUtils.requireInt(definition, "window", "period", "barCount"));
            case "ADD", "PLUS" -> applyBinaryNumericOperation(definition, numeric, factory, series, cache, BinaryOperation.ADD);
            case "SUBTRACT", "MINUS" -> applyBinaryNumericOperation(definition, numeric, factory, series, cache, BinaryOperation.SUBTRACT);
            case "MULTIPLY", "TIMES" -> applyBinaryNumericOperation(definition, numeric, factory, series, cache, BinaryOperation.MULTIPLY);
            case "DIVIDE" -> applyBinaryNumericOperation(definition, numeric, factory, series, cache, BinaryOperation.DIVIDE);
            default -> throw new InvalidStrategyConfigurationException("Unsupported transform operation: " + operation);
        };
    }

    private Indicator<Num> buildLag(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    ) {
        return new PreviousValueIndicator(input, IndicatorUtils.getInt(definition, "lag", 1, "offset", "periods"));
    }

    private Indicator<Num> applyBinaryNumericOperation(
            IndicatorDefinition definition,
            NumericIndicator numeric,
            IndicatorFactory factory,
            BarSeries series,
            Map<String, Indicator<Num>> cache,
            BinaryOperation operation
    ) {
        Object rightInput = IndicatorUtils.getObject(definition, "rightInput", "rightIndicator", "operand");
        if (rightInput != null) {
            Indicator<Num> right = factory.resolveInput(rightInput, series, cache, null);
            return switch (operation) {
                case ADD -> numeric.plus(right);
                case SUBTRACT -> numeric.minus(right);
                case MULTIPLY -> numeric.multipliedBy(right);
                case DIVIDE -> numeric.dividedBy(right);
            };
        }

        double value = IndicatorUtils.getDouble(definition, "value", Double.NaN, "constant", "operandValue");
        if (!Double.isFinite(value)) {
            throw IndicatorUtils.invalidParam(definition, "value", "is required for transform operation " + operation.name());
        }

        return switch (operation) {
            case ADD -> numeric.plus(value);
            case SUBTRACT -> numeric.minus(value);
            case MULTIPLY -> numeric.multipliedBy(value);
            case DIVIDE -> numeric.dividedBy(value);
        };
    }

    private enum BinaryOperation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE
    }
}
