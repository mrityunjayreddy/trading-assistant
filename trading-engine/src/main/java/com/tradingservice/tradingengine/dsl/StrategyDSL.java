package com.tradingservice.tradingengine.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * JSON strategy definition produced by AI/LLM services or the frontend builder.
 * Converted to the engine's internal {@code StrategyDefinition} by {@link DslStrategyAdapter}.
 *
 * <p>Entry and exit are human-readable boolean expressions, e.g.
 * {@code "rsi < 30 AND close > sma50"}</p>
 *
 * <p>Supported operators: {@code <  >  <=  >=  ==  !=  AND  OR
 * cross_above  cross_below}.</p>
 *
 * <p>Supported expression forms: infix ({@code sma50 cross_above sma200}) and
 * function-call ({@code cross_above(sma50, sma200)}) are both accepted.</p>
 *
 * <p>Source values: USER | LLM | EVOLVED | BUILTIN</p>
 */
public record StrategyDSL(
        @JsonProperty("name")       String name,
        @JsonProperty("version")    String version,
        @JsonProperty("source")     String source,
        @JsonProperty("indicators") List<IndicatorConfig> indicators,
        @JsonProperty("entry")      String entry,
        @JsonProperty("exit")       String exit,
        @JsonProperty("risk")       RiskConfig risk
) {
}