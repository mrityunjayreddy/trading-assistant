package com.tradingservice.strategyservice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Full strategy definition in the JSON StrategyDSL format.
 * Stored as JSONB in the {@code strategies.dsl} column.
 */
public record StrategyDSL(
        @JsonProperty("name")       String name,
        @JsonProperty("version")    String version,
        @JsonProperty("source")     String source,
        @JsonProperty("indicators") List<IndicatorConfig> indicators,
        @JsonProperty("entry")      String entry,
        @JsonProperty("exit")       String exit,
        @JsonProperty("risk")       RiskConfig risk
) {}