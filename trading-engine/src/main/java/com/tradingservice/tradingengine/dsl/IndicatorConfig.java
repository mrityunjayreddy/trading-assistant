package com.tradingservice.tradingengine.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Indicator definition in the JSON StrategyDSL.
 * Maps 1-to-1 with the engine's existing {@code IndicatorDefinition} DTO.
 *
 * Example:
 * <pre>{"id":"rsi","type":"RSI","params":{"period":14}}</pre>
 */
public record IndicatorConfig(
        @JsonProperty("id")     String id,
        @JsonProperty("type")   String type,
        @JsonProperty("params") Map<String, Object> params
) {
}