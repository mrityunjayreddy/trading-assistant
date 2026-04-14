package com.tradingservice.strategyservice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Risk management parameters for a StrategyDSL.
 */
public record RiskConfig(
        @JsonProperty("stopLossPct")     Double stopLossPct,
        @JsonProperty("takeProfitPct")   Double takeProfitPct,
        @JsonProperty("positionSizePct") Double positionSizePct,
        @JsonProperty("trailingStop")    Boolean trailingStop
) {}