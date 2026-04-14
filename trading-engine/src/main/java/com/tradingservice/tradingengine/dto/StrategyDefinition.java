package com.tradingservice.tradingengine.dto;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyDefinition {

    @Valid
    @Builder.Default
    private List<IndicatorDefinition> indicators = new ArrayList<>();

    @Valid
    private RuleDefinition entryRules;

    @Valid
    private RuleDefinition exitRules;
}
