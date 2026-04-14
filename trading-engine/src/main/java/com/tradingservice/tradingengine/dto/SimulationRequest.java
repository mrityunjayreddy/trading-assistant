package com.tradingservice.tradingengine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {

    @NotBlank
    private String symbol;

    @NotBlank
    private String interval;

    @NotBlank
    private String strategy;

    @NotNull
    @Builder.Default
    private Map<String, Object> params = new LinkedHashMap<>();

    @Valid
    @Builder.Default
    private List<IndicatorDefinition> indicators = new ArrayList<>();

    @Valid
    private RuleDefinition entryRules;

    @Valid
    private RuleDefinition exitRules;

    @Valid
    @NotNull
    @Builder.Default
    private ExecutionModelRequest execution = ExecutionModelRequest.builder().build();

    @Valid
    @NotNull
    @Builder.Default
    private SimulationAssumptions assumptions = SimulationAssumptions.builder().build();

    @Valid
    @NotNull
    @Builder.Default
    private SimulationRange range = SimulationRange.builder().build();
}
