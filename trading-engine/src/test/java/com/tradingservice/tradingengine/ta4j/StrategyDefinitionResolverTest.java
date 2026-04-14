package com.tradingservice.tradingengine.ta4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.dto.RuleComparator;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategyDefinitionResolverTest {

    private final StrategyDefinitionResolver resolver = new StrategyDefinitionResolver();

    // ── Named strategy routing ────────────────────────────────────────────────

    @Test
    void shouldResolveMovingAverageCrossover() {
        StrategyDefinition def = resolver.resolve(
                "MA_CROSSOVER",
                Map.of("shortWindow", 10, "longWindow", 50),
                null, null, null
        );

        assertThat(def.getIndicators()).hasSize(2);
        assertThat(def.getIndicators().get(0).getId()).isEqualTo("fastMa");
        assertThat(def.getIndicators().get(0).getType()).isEqualTo("SMA");
        assertThat(def.getIndicators().get(0).getParams()).containsEntry("window", 10);
        assertThat(def.getIndicators().get(1).getId()).isEqualTo("slowMa");
        assertThat(def.getIndicators().get(1).getParams()).containsEntry("window", 50);
        assertThat(def.getEntryRules().getLeft()).isEqualTo("fastMa");
        assertThat(def.getEntryRules().getOperator()).isEqualTo(RuleComparator.CROSS_ABOVE);
        assertThat(def.getEntryRules().getRightIndicator()).isEqualTo("slowMa");
        assertThat(def.getExitRules().getOperator()).isEqualTo(RuleComparator.CROSS_BELOW);
    }

    @Test
    void shouldResolveMovingAverageCrossoverCaseInsensitive() {
        StrategyDefinition def = resolver.resolve(
                "ma_crossover",
                Map.of("shortWindow", 5, "longWindow", 20),
                null, null, null
        );

        assertThat(def.getIndicators()).hasSize(2);
        assertThat(def.getIndicators().get(0).getParams()).containsEntry("window", 5);
    }

    @Test
    void shouldResolveRsiStrategy() {
        StrategyDefinition def = resolver.resolve(
                "RSI",
                Map.of("period", 14, "overbought", 70.0, "oversold", 30.0),
                null, null, null
        );

        assertThat(def.getIndicators()).hasSize(1);
        assertThat(def.getIndicators().get(0).getId()).isEqualTo("rsi");
        assertThat(def.getIndicators().get(0).getType()).isEqualTo("RSI");
        assertThat(def.getIndicators().get(0).getParams()).containsEntry("period", 14);
        assertThat(def.getEntryRules().getLeft()).isEqualTo("rsi");
        assertThat(def.getEntryRules().getOperator()).isEqualTo(RuleComparator.CROSS_ABOVE);
        assertThat(def.getEntryRules().getRightValue()).isEqualTo(30.0);
        assertThat(def.getExitRules().getRightValue()).isEqualTo(70.0);
        assertThat(def.getExitRules().getOperator()).isEqualTo(RuleComparator.CROSS_BELOW);
    }

    @Test
    void shouldResolveBollingerBands() {
        StrategyDefinition def = resolver.resolve(
                "BOLLINGER_BANDS",
                Map.of("window", 20, "stdDevMultiplier", 2.0),
                null, null, null
        );

        assertThat(def.getIndicators()).hasSize(1);
        assertThat(def.getIndicators().get(0).getType()).isEqualTo("BOLLINGER");
        assertThat(def.getIndicators().get(0).getParams()).containsEntry("window", 20);
        assertThat(def.getIndicators().get(0).getParams()).containsEntry("stdDevMultiplier", 2.0);
        assertThat(def.getEntryRules().getLeft()).isEqualTo("close");
        assertThat(def.getEntryRules().getOperator()).isEqualTo(RuleComparator.CROSS_ABOVE);
        assertThat(def.getEntryRules().getRightIndicator()).isEqualTo("bb.lower");
        assertThat(def.getExitRules().getRightIndicator()).isEqualTo("bb.upper");
    }

    @Test
    void shouldResolveBuyAndHold() {
        StrategyDefinition def = resolver.resolve(
                "BUY_AND_HOLD",
                Map.of(),
                null, null, null
        );

        assertThat(def.getIndicators()).isEmpty();
        // Entry always fires (close > 0), exit never fires (close < -1)
        assertThat(def.getEntryRules().getRightValue()).isEqualTo(0.0);
        assertThat(def.getEntryRules().getOperator()).isEqualTo(RuleComparator.GREATER_THAN);
        assertThat(def.getExitRules().getRightValue()).isEqualTo(-1.0);
        assertThat(def.getExitRules().getOperator()).isEqualTo(RuleComparator.LESS_THAN);
    }

    // ── DSL routing ───────────────────────────────────────────────────────────

    @Test
    void shouldUseDslWhenIndicatorsAndRulesProvided() {
        IndicatorDefinition sma = IndicatorDefinition.builder()
                .id("myMa")
                .type("SMA")
                .params(Map.of("window", 10))
                .input("close")
                .build();
        RuleDefinition entry = RuleDefinition.builder()
                .left("myMa")
                .operator(RuleComparator.GREATER_THAN)
                .rightValue(0.0)
                .build();
        RuleDefinition exit = RuleDefinition.builder()
                .left("myMa")
                .operator(RuleComparator.LESS_THAN)
                .rightValue(99_999.0)
                .build();

        StrategyDefinition def = resolver.resolve("MA_CROSSOVER", Map.of(), List.of(sma), entry, exit);

        // DSL takes precedence — indicators come from our custom list, not the MA_CROSSOVER preset
        assertThat(def.getIndicators()).hasSize(1);
        assertThat(def.getIndicators().get(0).getId()).isEqualTo("myMa");
        assertThat(def.getEntryRules().getLeft()).isEqualTo("myMa");
    }

    @Test
    void shouldUseDslWithRulesEvenWhenIndicatorListIsEmpty() {
        // Rules without indicators is valid DSL (e.g. "close > 100")
        RuleDefinition entry = RuleDefinition.builder()
                .left("close")
                .operator(RuleComparator.GREATER_THAN)
                .rightValue(100.0)
                .build();
        RuleDefinition exit = RuleDefinition.builder()
                .left("close")
                .operator(RuleComparator.LESS_THAN)
                .rightValue(50.0)
                .build();

        StrategyDefinition def = resolver.resolve("MA_CROSSOVER", Map.of(), List.of(), entry, exit);

        assertThat(def.getIndicators()).isEmpty();
        assertThat(def.getEntryRules().getRightValue()).isEqualTo(100.0);
        assertThat(def.getExitRules().getRightValue()).isEqualTo(50.0);
    }

    @Test
    void shouldApplyDslIndicatorParameterOverrides() {
        IndicatorDefinition sma = IndicatorDefinition.builder()
                .id("ma1")
                .type("SMA")
                .params(new java.util.LinkedHashMap<>(Map.of("window", 10)))
                .input("close")
                .build();
        RuleDefinition entry = RuleDefinition.builder().left("ma1").operator(RuleComparator.GREATER_THAN).rightValue(0.0).build();
        RuleDefinition exit = RuleDefinition.builder().left("ma1").operator(RuleComparator.LESS_THAN).rightValue(99_999.0).build();

        // Override indicators.ma1.params.window → 25
        StrategyDefinition def = resolver.resolve(
                "CUSTOM",
                Map.of("indicators.ma1.params.window", 25),
                List.of(sma),
                entry,
                exit
        );

        assertThat(def.getIndicators().get(0).getParams()).containsEntry("window", 25);
    }

    // ── Validation errors ─────────────────────────────────────────────────────

    @Test
    void shouldThrowWhenOnlyEntryRulesProvided() {
        RuleDefinition entry = RuleDefinition.builder()
                .left("close")
                .operator(RuleComparator.GREATER_THAN)
                .rightValue(0.0)
                .build();

        assertThatThrownBy(() -> resolver.resolve("MA_CROSSOVER", Map.of(), List.of(), entry, null))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("entryRules and exitRules");
    }

    @Test
    void shouldThrowWhenOnlyExitRulesProvided() {
        RuleDefinition exit = RuleDefinition.builder()
                .left("close")
                .operator(RuleComparator.LESS_THAN)
                .rightValue(99_999.0)
                .build();

        assertThatThrownBy(() -> resolver.resolve("MA_CROSSOVER", Map.of(), List.of(), null, exit))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("entryRules and exitRules");
    }

    @Test
    void shouldThrowForUnknownStrategyName() {
        assertThatThrownBy(() -> resolver.resolve("UNKNOWN_STRATEGY", Map.of(), null, null, null))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("UNKNOWN_STRATEGY");
    }

    @Test
    void shouldThrowWhenShortWindowEqualsLongWindow() {
        assertThatThrownBy(() -> resolver.resolve(
                "MA_CROSSOVER",
                Map.of("shortWindow", 20, "longWindow", 20),
                null, null, null
        ))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("shortWindow");
    }

    @Test
    void shouldThrowWhenShortWindowExceedsLongWindow() {
        assertThatThrownBy(() -> resolver.resolve(
                "MA_CROSSOVER",
                Map.of("shortWindow", 50, "longWindow", 10),
                null, null, null
        ))
                .isInstanceOf(InvalidStrategyConfigurationException.class);
    }

    @Test
    void shouldUseDefaultRsiParametersWhenNotProvided() {
        StrategyDefinition def = resolver.resolve("RSI", Map.of(), null, null, null);

        assertThat(def.getIndicators().get(0).getParams()).containsEntry("period", 14);
        assertThat(def.getEntryRules().getRightValue()).isEqualTo(30.0);
        assertThat(def.getExitRules().getRightValue()).isEqualTo(70.0);
    }
}