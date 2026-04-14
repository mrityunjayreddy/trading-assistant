package com.tradingservice.tradingengine.ta4j;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.dto.RuleComparator;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.strategy.BollingerBandsStrategy;
import com.tradingservice.tradingengine.strategy.BuyAndHoldStrategy;
import com.tradingservice.tradingengine.strategy.MovingAverageStrategy;
import com.tradingservice.tradingengine.strategy.RsiStrategy;
import com.tradingservice.tradingengine.strategy.StrategyParameterReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StrategyDefinitionResolver {

    public StrategyDefinition resolve(
            String strategyName,
            Map<String, Object> params,
            List<IndicatorDefinition> indicators,
            RuleDefinition entryRules,
            RuleDefinition exitRules
    ) {
        if (hasCustomDsl(indicators, entryRules, exitRules)) {
            StrategyDefinition definition = StrategyDefinition.builder()
                    .indicators(copyIndicators(indicators))
                    .entryRules(copyRule(entryRules))
                    .exitRules(copyRule(exitRules))
                    .build();
            return applyOverrides(definition, params);
        }

        StrategyParameterReader reader = new StrategyParameterReader(params);
        String normalizedName = normalize(strategyName);

        return switch (normalizedName) {
            case MovingAverageStrategy.NAME -> movingAverageDefinition(reader);
            case RsiStrategy.NAME -> rsiDefinition(reader);
            case BollingerBandsStrategy.NAME -> bollingerDefinition(reader);
            case BuyAndHoldStrategy.NAME -> buyAndHoldDefinition();
            default -> throw new InvalidStrategyConfigurationException("Unsupported strategy: " + strategyName);
        };
    }

    private boolean hasCustomDsl(
            List<IndicatorDefinition> indicators,
            RuleDefinition entryRules,
            RuleDefinition exitRules
    ) {
        boolean hasIndicators = indicators != null && !indicators.isEmpty();
        boolean hasRules = entryRules != null || exitRules != null;
        if (!hasIndicators && !hasRules) {
            return false;
        }
        if (entryRules == null || exitRules == null) {
            throw new InvalidStrategyConfigurationException("Custom DSL requires both entryRules and exitRules");
        }
        return true;
    }

    private StrategyDefinition movingAverageDefinition(StrategyParameterReader reader) {
        int shortWindow = reader.getInt("shortWindow", 10);
        int longWindow = reader.getInt("longWindow", 50);
        if (shortWindow >= longWindow) {
            throw new InvalidStrategyConfigurationException("shortWindow must be less than longWindow");
        }

        return StrategyDefinition.builder()
                .indicators(List.of(
                        indicator("fastMa", "SMA", Map.of("window", shortWindow), "close"),
                        indicator("slowMa", "SMA", Map.of("window", longWindow), "close")
                ))
                .entryRules(rule("fastMa", RuleComparator.CROSS_ABOVE, "slowMa"))
                .exitRules(rule("fastMa", RuleComparator.CROSS_BELOW, "slowMa"))
                .build();
    }

    private StrategyDefinition rsiDefinition(StrategyParameterReader reader) {
        int period = reader.getInt("period", 14);
        double overbought = reader.getDouble("overbought", 70.0);
        double oversold = reader.getDouble("oversold", 30.0);

        return StrategyDefinition.builder()
                .indicators(List.of(indicator("rsi", "RSI", Map.of("period", period), "close")))
                .entryRules(rule("rsi", RuleComparator.CROSS_ABOVE, oversold))
                .exitRules(rule("rsi", RuleComparator.CROSS_BELOW, overbought))
                .build();
    }

    private StrategyDefinition bollingerDefinition(StrategyParameterReader reader) {
        int window = reader.getInt("window", 20);
        double stdDevMultiplier = reader.getDouble("stdDevMultiplier", 2.0);

        return StrategyDefinition.builder()
                .indicators(List.of(indicator("bb", "BOLLINGER", Map.of(
                        "window", window,
                        "stdDevMultiplier", stdDevMultiplier
                ), "close")))
                .entryRules(rule("close", RuleComparator.CROSS_ABOVE, "bb.lower"))
                .exitRules(rule("close", RuleComparator.CROSS_BELOW, "bb.upper"))
                .build();
    }

    private StrategyDefinition buyAndHoldDefinition() {
        return StrategyDefinition.builder()
                .indicators(List.of())
                .entryRules(rule("close", RuleComparator.GREATER_THAN, 0.0))
                .exitRules(rule("close", RuleComparator.LESS_THAN, -1.0))
                .build();
    }

    private StrategyDefinition applyOverrides(StrategyDefinition definition, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return definition;
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("indicators.")) {
                applyIndicatorOverride(definition, key, entry.getValue());
            }
        }
        return definition;
    }

    private void applyIndicatorOverride(StrategyDefinition definition, String path, Object value) {
        String[] parts = path.split("\\.");
        if (parts.length < 4 || !"params".equals(parts[2])) {
            throw new InvalidStrategyConfigurationException("Unsupported DSL optimization path: " + path);
        }

        IndicatorDefinition indicator = definition.getIndicators().stream()
                .filter(candidate -> candidate.getId().equals(parts[1]))
                .findFirst()
                .orElseThrow(() -> new InvalidStrategyConfigurationException("Unknown indicator override target: " + parts[1]));

        indicator.getParams().put(parts[3], value);
    }

    private IndicatorDefinition indicator(String id, String type, Map<String, Object> params, Object input) {
        return IndicatorDefinition.builder()
                .id(id)
                .type(type)
                .params(new LinkedHashMap<>(params))
                .input(input)
                .build();
    }

    private RuleDefinition rule(String left, RuleComparator operator, String rightIndicator) {
        return RuleDefinition.builder()
                .left(left)
                .operator(operator)
                .rightIndicator(rightIndicator)
                .build();
    }

    private RuleDefinition rule(String left, RuleComparator operator, double rightValue) {
        return RuleDefinition.builder()
                .left(left)
                .operator(operator)
                .rightValue(rightValue)
                .build();
    }

    private List<IndicatorDefinition> copyIndicators(List<IndicatorDefinition> indicators) {
        List<IndicatorDefinition> copies = new ArrayList<>();
        for (IndicatorDefinition indicator : indicators) {
            copies.add(IndicatorDefinition.builder()
                    .id(indicator.getId())
                    .type(indicator.getType())
                    .subType(indicator.getSubType())
                    .params(indicator.getParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(indicator.getParams()))
                    .input(copyInput(indicator.getInput()))
                    .build());
        }
        return copies;
    }

    private Object copyInput(Object input) {
        if (input instanceof IndicatorDefinition nestedIndicator) {
            return IndicatorDefinition.builder()
                    .id(nestedIndicator.getId())
                    .type(nestedIndicator.getType())
                    .subType(nestedIndicator.getSubType())
                    .params(nestedIndicator.getParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nestedIndicator.getParams()))
                    .input(copyInput(nestedIndicator.getInput()))
                    .build();
        }
        return input;
    }

    private RuleDefinition copyRule(RuleDefinition source) {
        if (source == null) {
            return null;
        }

        List<RuleDefinition> nested = new ArrayList<>();
        if (source.getRules() != null) {
            for (RuleDefinition child : source.getRules()) {
                nested.add(copyRule(child));
            }
        }

        return RuleDefinition.builder()
                .logicalOperator(source.getLogicalOperator())
                .rules(nested)
                .left(source.getLeft())
                .operator(source.getOperator())
                .rightIndicator(source.getRightIndicator())
                .rightValue(source.getRightValue())
                .rightValue2(source.getRightValue2())
                .build();
    }

    private String normalize(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            throw new InvalidStrategyConfigurationException("Strategy name must be provided");
        }
        return strategyName.trim().toUpperCase(Locale.ROOT);
    }
}
