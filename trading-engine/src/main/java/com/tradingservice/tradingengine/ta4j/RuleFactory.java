package com.tradingservice.tradingengine.ta4j;

import com.tradingservice.tradingengine.dto.LogicalOperator;
import com.tradingservice.tradingengine.dto.RuleComparator;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.IsHighestRule;
import org.ta4j.core.rules.IsLowestRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

@Component
public class RuleFactory {

    public Rule build(RuleDefinition definition, Map<String, Indicator<Num>> indicators, BarSeries series) {
        if (definition == null) {
            throw new InvalidStrategyConfigurationException("Rule definition must be provided");
        }

        if (definition.getRules() != null && !definition.getRules().isEmpty()) {
            return combine(definition, indicators, series);
        }

        if (definition.getLeft() == null || definition.getOperator() == null) {
            throw new InvalidStrategyConfigurationException("Leaf rules must declare left and operator");
        }

        Indicator<Num> left = resolveIndicator(definition.getLeft(), indicators);
        RuleComparator op = definition.getOperator();

        // Operators that don't use the standard right-side indicator/value resolution
        if (op == RuleComparator.IS_BETWEEN) {
            return buildIsBetween(left, definition, series);
        }
        if (op == RuleComparator.NBAR_HIGH) {
            int n = requireIntValue(definition, "NBAR_HIGH");
            return new IsHighestRule(left, n);
        }
        if (op == RuleComparator.NBAR_LOW) {
            int n = requireIntValue(definition, "NBAR_LOW");
            return new IsLowestRule(left, n);
        }
        if (op == RuleComparator.INCREASED_BY_PCT) {
            return buildIncreasedByPct(left, definition, series);
        }

        // All remaining operators use a resolved right-side indicator or constant
        Indicator<Num> right = resolveRightIndicator(definition, indicators, series);

        return switch (op) {
            case GREATER_THAN -> new OverIndicatorRule(left, right);
            case LESS_THAN    -> new UnderIndicatorRule(left, right);
            case CROSS_ABOVE  -> new CrossedUpIndicatorRule(left, right);
            case CROSS_BELOW  -> new CrossedDownIndicatorRule(left, right);
            // EQUAL_TO: left is neither strictly above nor below → left ≈ right
            case EQUAL_TO     -> new OverIndicatorRule(left, right).negation()
                                     .and(new UnderIndicatorRule(left, right).negation());
            // These are resolved before the switch — arms required for exhaustive coverage
            case IS_BETWEEN, NBAR_HIGH, NBAR_LOW, INCREASED_BY_PCT ->
                    throw new InvalidStrategyConfigurationException("Operator " + op + " must be handled before right-side resolution");
        };
    }

    // ── Operator helpers ──────────────────────────────────────────────────────

    /**
     * IS_BETWEEN: rightValue (lower bound) ≤ left ≤ rightValue2 (upper bound).
     * Expressed as: NOT(left < lower) AND NOT(left > upper)
     */
    private Rule buildIsBetween(Indicator<Num> left, RuleDefinition def, BarSeries series) {
        if (def.getRightValue() == null || def.getRightValue2() == null) {
            throw new InvalidStrategyConfigurationException(
                    "IS_BETWEEN requires rightValue (lower) and rightValue2 (upper)");
        }
        Indicator<Num> lower = constant(series, def.getRightValue());
        Indicator<Num> upper = constant(series, def.getRightValue2());
        // left >= lower AND left <= upper
        return new UnderIndicatorRule(left, lower).negation()
                .and(new OverIndicatorRule(left, upper).negation());
    }

    /**
     * INCREASED_BY_PCT: current value has risen ≥ rightValue% compared to
     * rightValue2 bars ago (default 1 bar ago).
     * Condition: left ≥ prev × (1 + pct/100)  →  NOT(left < prev × multiplier)
     */
    private Rule buildIncreasedByPct(Indicator<Num> left, RuleDefinition def, BarSeries series) {
        if (def.getRightValue() == null) {
            throw new InvalidStrategyConfigurationException("INCREASED_BY_PCT requires rightValue (percent)");
        }
        int barsAgo = def.getRightValue2() != null ? Math.max(1, def.getRightValue2().intValue()) : 1;
        double multiplier = 1.0 + def.getRightValue() / 100.0;

        PreviousValueIndicator prev = new PreviousValueIndicator(left, barsAgo);
        Indicator<Num> threshold = CombineIndicator.multiply(prev,
                new ConstantIndicator<>(series, series.numFactory().numOf(multiplier)));

        // left >= threshold
        return new UnderIndicatorRule(left, threshold).negation();
    }

    // ── Composite rule ────────────────────────────────────────────────────────

    private Rule combine(RuleDefinition definition, Map<String, Indicator<Num>> indicators, BarSeries series) {
        List<RuleDefinition> nestedRules = definition.getRules();
        if (nestedRules.isEmpty()) {
            throw new InvalidStrategyConfigurationException("Composite rule must contain nested rules");
        }

        LogicalOperator operator = definition.getLogicalOperator();
        if (operator == null) {
            throw new InvalidStrategyConfigurationException("Composite rule must declare logicalOperator");
        }

        Rule combined = build(nestedRules.get(0), indicators, series);
        for (int index = 1; index < nestedRules.size(); index++) {
            Rule next = build(nestedRules.get(index), indicators, series);
            combined = operator == LogicalOperator.AND ? combined.and(next) : combined.or(next);
        }
        return combined;
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    private Indicator<Num> resolveRightIndicator(
            RuleDefinition definition,
            Map<String, Indicator<Num>> indicators,
            BarSeries series
    ) {
        if (definition.getRightIndicator() != null && definition.getRightValue() != null) {
            throw new InvalidStrategyConfigurationException(
                    "Rule must use either rightIndicator or rightValue, not both");
        }
        if (definition.getRightIndicator() != null) {
            return resolveIndicator(definition.getRightIndicator(), indicators);
        }
        if (definition.getRightValue() != null) {
            return constant(series, definition.getRightValue());
        }
        throw new InvalidStrategyConfigurationException("Rule must declare either rightIndicator or rightValue");
    }

    private Indicator<Num> resolveIndicator(String reference, Map<String, Indicator<Num>> indicators) {
        Indicator<Num> indicator = indicators.get(reference.trim().toLowerCase(Locale.ROOT));
        if (indicator == null) {
            throw new InvalidStrategyConfigurationException(
                    "Unknown indicator reference in rule: " + reference);
        }
        return indicator;
    }

    private Indicator<Num> constant(BarSeries series, double value) {
        return new ConstantIndicator<>(series, series.numFactory().numOf(value));
    }

    private int requireIntValue(RuleDefinition def, String operatorName) {
        if (def.getRightValue() == null) {
            throw new InvalidStrategyConfigurationException(
                    operatorName + " requires rightValue (number of bars)");
        }
        return Math.max(1, def.getRightValue().intValue());
    }

    public Rule alwaysFalse() {
        return new BooleanRule(false);
    }
}
