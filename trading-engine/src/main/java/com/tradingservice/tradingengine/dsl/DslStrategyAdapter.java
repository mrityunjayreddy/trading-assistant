package com.tradingservice.tradingengine.dsl;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts a {@link StrategyDSL} (JSON format from AI/LLM/frontend) into the
 * engine's internal {@link StrategyDefinition}, which flows through the existing
 * {@code Ta4jStrategyBuilder} → {@code IndicatorFactory} → {@code RuleFactory} pipeline.
 *
 * <p>No existing strategy classes are modified — this is purely additive.</p>
 */
@Component
public class DslStrategyAdapter {

    /**
     * Converts a {@link StrategyDSL} to a {@link StrategyDefinition}.
     * The result is passed directly to {@code TradingSimulationService.simulate()}.
     */
    public StrategyDefinition toStrategyDefinition(StrategyDSL dsl) {
        if (dsl == null) {
            throw new InvalidStrategyConfigurationException("StrategyDSL must not be null");
        }
        if (dsl.entry() == null || dsl.entry().isBlank()) {
            throw new InvalidStrategyConfigurationException("StrategyDSL.entry must not be blank");
        }
        if (dsl.exit() == null || dsl.exit().isBlank()) {
            throw new InvalidStrategyConfigurationException("StrategyDSL.exit must not be blank");
        }

        List<IndicatorDefinition> indicators = convertIndicators(dsl.indicators());
        RuleDefinition entryRule = ExpressionParser.parse(dsl.entry());
        RuleDefinition exitRule  = ExpressionParser.parse(dsl.exit());

        return StrategyDefinition.builder()
                .indicators(indicators)
                .entryRules(entryRule)
                .exitRules(exitRule)
                .build();
    }

    // -------------------------------------------------------------------------

    private List<IndicatorDefinition> convertIndicators(List<IndicatorConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return new ArrayList<>();
        }

        List<IndicatorDefinition> definitions = new ArrayList<>(configs.size());
        for (IndicatorConfig config : configs) {
            if (config.id() == null || config.id().isBlank()) {
                throw new InvalidStrategyConfigurationException("Each IndicatorConfig must have a non-blank id");
            }
            if (config.type() == null || config.type().isBlank()) {
                throw new InvalidStrategyConfigurationException("Each IndicatorConfig must have a non-blank type (id=" + config.id() + ")");
            }

            definitions.add(
                IndicatorDefinition.builder()
                    .id(config.id())
                    .type(config.type())
                    .params(config.params() != null ? new LinkedHashMap<>(config.params()) : new LinkedHashMap<>())
                    .build()
            );
        }
        return definitions;
    }
}