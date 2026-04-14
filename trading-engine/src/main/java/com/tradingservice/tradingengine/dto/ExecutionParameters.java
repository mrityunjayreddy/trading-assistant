package com.tradingservice.tradingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionParameters {

    @Builder.Default
    private Double allocationPercent = 100.0;

    @Builder.Default
    private Double fixedAmount = 1000.0;
}
