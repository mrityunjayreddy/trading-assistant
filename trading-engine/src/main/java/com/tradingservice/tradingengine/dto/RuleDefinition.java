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
public class RuleDefinition {

    private LogicalOperator logicalOperator;

    @Valid
    @Builder.Default
    private List<RuleDefinition> rules = new ArrayList<>();

    private String left;

    private RuleComparator operator;

    private String rightIndicator;

    private Double rightValue;

    /** Second threshold — used by IS_BETWEEN (upper bound) and INCREASED_BY_PCT (lookback bars). */
    private Double rightValue2;
}
