package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.dto.StrategyDescriptor;
import com.tradingservice.tradingengine.dto.StrategyParameterDescriptor;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.indicator.IndicatorService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class StrategyRegistry {

    private final Map<String, Supplier<Strategy>> defaultStrategies = new ConcurrentHashMap<>();
    private final Map<String, Function<Map<String, Object>, Strategy>> configuredStrategies = new ConcurrentHashMap<>();
    private final Map<String, StrategyDescriptor> descriptors = new ConcurrentHashMap<>();

    public StrategyRegistry(IndicatorService indicatorService) {
        register(
                MovingAverageStrategy.NAME,
                descriptor(
                        MovingAverageStrategy.NAME,
                        "Moving Average Crossover",
                        "Buys when the short moving average crosses above the long moving average, and sells on the reverse crossover.",
                        List.of(
                                StrategyParameterDescriptor.builder()
                                        .name("shortWindow")
                                        .label("Short Window")
                                        .type("number")
                                        .defaultValue(10)
                                        .minValue(1)
                                        .maxValue(499)
                                        .required(true)
                                        .description("Number of candles for the fast moving average.")
                                        .build(),
                                StrategyParameterDescriptor.builder()
                                        .name("longWindow")
                                        .label("Long Window")
                                        .type("number")
                                        .defaultValue(50)
                                        .minValue(2)
                                        .maxValue(500)
                                        .required(true)
                                        .description("Number of candles for the slow moving average.")
                                        .build()
                        )
                ),
                () -> new MovingAverageStrategy(indicatorService, 10, 50),
                parameters -> MovingAverageStrategy.from(parameters, indicatorService)
        );

        register(
                RsiStrategy.NAME,
                descriptor(
                        RsiStrategy.NAME,
                        "RSI Mean Reversion",
                        "Buys when RSI recovers from oversold territory and sells when RSI rolls over from overbought territory.",
                        List.of(
                                StrategyParameterDescriptor.builder()
                                        .name("period")
                                        .label("Period")
                                        .type("number")
                                        .defaultValue(14)
                                        .minValue(2)
                                        .maxValue(200)
                                        .required(true)
                                        .description("Number of candles used to smooth RSI.")
                                        .build(),
                                StrategyParameterDescriptor.builder()
                                        .name("overbought")
                                        .label("Overbought")
                                        .type("number")
                                        .defaultValue(70.0)
                                        .minValue(50)
                                        .maxValue(100)
                                        .required(true)
                                        .description("Sell threshold for RSI.")
                                        .build(),
                                StrategyParameterDescriptor.builder()
                                        .name("oversold")
                                        .label("Oversold")
                                        .type("number")
                                        .defaultValue(30.0)
                                        .minValue(0)
                                        .maxValue(50)
                                        .required(true)
                                        .description("Buy threshold for RSI.")
                                        .build()
                        )
                ),
                () -> new RsiStrategy(indicatorService, 14, 70.0, 30.0),
                parameters -> RsiStrategy.from(parameters, indicatorService)
        );

        register(
                BollingerBandsStrategy.NAME,
                descriptor(
                        BollingerBandsStrategy.NAME,
                        "Bollinger Bands Reversion",
                        "Buys when price crosses back above the lower band and sells when price drops back below the upper band.",
                        List.of(
                                StrategyParameterDescriptor.builder()
                                        .name("window")
                                        .label("Window")
                                        .type("number")
                                        .defaultValue(20)
                                        .minValue(2)
                                        .maxValue(300)
                                        .required(true)
                                        .description("Number of candles used for the moving average and deviation.")
                                        .build(),
                                StrategyParameterDescriptor.builder()
                                        .name("stdDevMultiplier")
                                        .label("Std Dev Multiplier")
                                        .type("number")
                                        .defaultValue(2.0)
                                        .minValue(1)
                                        .maxValue(5)
                                        .required(true)
                                        .description("Band width in standard deviations.")
                                        .build()
                        )
                ),
                () -> new BollingerBandsStrategy(indicatorService, 20, 2.0),
                parameters -> BollingerBandsStrategy.from(parameters, indicatorService)
        );

        register(
                BuyAndHoldStrategy.NAME,
                descriptor(
                        BuyAndHoldStrategy.NAME,
                        "Buy And Hold",
                        "Buys the first candle in the range and holds the position through the full simulation.",
                        List.of()
                ),
                BuyAndHoldStrategy::new,
                ignored -> new BuyAndHoldStrategy()
        );
    }

    public void register(
            String strategyName,
            StrategyDescriptor descriptor,
            Supplier<Strategy> defaultSupplier,
            Function<Map<String, Object>, Strategy> configuredStrategySupplier
    ) {
        String normalizedName = normalize(strategyName);
        defaultStrategies.put(normalizedName, defaultSupplier);
        configuredStrategies.put(normalizedName, configuredStrategySupplier);
        descriptors.put(normalizedName, descriptor);
    }

    public Strategy create(String strategyName, Map<String, Object> parameters) {
        Function<Map<String, Object>, Strategy> strategySupplier = configuredStrategies.get(normalize(strategyName));
        if (strategySupplier == null) {
            throw new InvalidStrategyConfigurationException("Unsupported strategy: " + strategyName);
        }
        return strategySupplier.apply(parameters == null ? Map.of() : Map.copyOf(parameters));
    }

    public Supplier<Strategy> getDefaultStrategySupplier(String strategyName) {
        Supplier<Strategy> supplier = defaultStrategies.get(normalize(strategyName));
        if (supplier == null) {
            throw new InvalidStrategyConfigurationException("Unsupported strategy: " + strategyName);
        }
        return supplier;
    }

    public List<StrategyDescriptor> getDescriptors() {
        return descriptors.values().stream()
                .sorted(Comparator.comparing(StrategyDescriptor::value))
                .toList();
    }

    private StrategyDescriptor descriptor(String value, String label, String description, List<StrategyParameterDescriptor> parameters) {
        return StrategyDescriptor.builder()
                .value(value)
                .label(label)
                .description(description)
                .parameters(parameters)
                .build();
    }

    private String normalize(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            throw new InvalidStrategyConfigurationException("Strategy name must be provided");
        }
        return strategyName.trim().toUpperCase(Locale.ROOT);
    }
}
