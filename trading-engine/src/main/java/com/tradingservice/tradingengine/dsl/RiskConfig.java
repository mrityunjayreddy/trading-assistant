package com.tradingservice.tradingengine.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Risk management parameters embedded in a {@link StrategyDSL}.
 * Informational for now — enforced at execution layer once implemented.
 */
public record RiskConfig(
        @JsonProperty("stopLossPct")     Double stopLossPct,
        @JsonProperty("takeProfitPct")   Double takeProfitPct,
        @JsonProperty("positionSizePct") Double positionSizePct,
        @JsonProperty("trailingStop")    Boolean trailingStop
) {
}