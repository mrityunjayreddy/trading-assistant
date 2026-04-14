package com.tradingservice.tradingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationAssumptions {

    @Builder.Default
    private Double initialBalance = 1000.0;

    @Builder.Default
    private Double feeRate = 0.0004;
}
