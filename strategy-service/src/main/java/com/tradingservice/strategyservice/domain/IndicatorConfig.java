package com.tradingservice.strategyservice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Describes a single technical indicator within a StrategyDSL.
 * Mirrors the record in trading-engine so DSL JSON round-trips cleanly.
 */
public record IndicatorConfig(
        @JsonProperty("id")     String id,
        @JsonProperty("type")   String type,
        @JsonProperty("params") Map<String, Object> params
) {}