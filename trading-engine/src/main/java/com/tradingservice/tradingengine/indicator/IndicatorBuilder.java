package com.tradingservice.tradingengine.indicator;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import java.util.Map;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

@FunctionalInterface
public interface IndicatorBuilder {

    Indicator<Num> build(
            IndicatorDefinition definition,
            Indicator<Num> input,
            BarSeries series,
            IndicatorFactory factory,
            Map<String, Indicator<Num>> cache
    );
}
