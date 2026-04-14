package com.tradingservice.tradingengine.ta4j;

import com.tradingservice.tradingengine.indicator.IndicatorFactory;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.num.Num;

@Component
@RequiredArgsConstructor
public class Ta4jStrategyBuilder {

    private final IndicatorFactory indicatorFactory;
    private final RuleFactory ruleFactory;

    public Strategy build(StrategyDefinition definition, BarSeries series) {
        Map<String, Indicator<Num>> indicators = indicatorFactory.buildIndicators(definition.getIndicators(), series);
        Rule entryRule = ruleFactory.build(definition.getEntryRules(), indicators, series);
        Rule exitRule = ruleFactory.build(definition.getExitRules(), indicators, series);
        return new BaseStrategy(entryRule, exitRule);
    }

    public Strategy buildInverse(StrategyDefinition definition, BarSeries series) {
        Map<String, Indicator<Num>> indicators = indicatorFactory.buildIndicators(definition.getIndicators(), series);
        Rule shortEntry = ruleFactory.build(definition.getExitRules(), indicators, series);
        Rule shortExit = ruleFactory.build(definition.getEntryRules(), indicators, series);
        return new BaseStrategy(shortEntry, shortExit);
    }

    public Rule createNoOpExitRule() {
        return ruleFactory.alwaysFalse();
    }
}
