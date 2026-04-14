package com.tradingservice.tradingengine.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record StrategyDescriptor(
        String value,
        String label,
        String description,
        List<StrategyParameterDescriptor> parameters
) {
}
