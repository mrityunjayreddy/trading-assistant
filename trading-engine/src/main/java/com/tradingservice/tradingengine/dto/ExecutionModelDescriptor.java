package com.tradingservice.tradingengine.dto;

import com.tradingservice.tradingengine.model.ExecutionModelType;
import java.util.List;
import lombok.Builder;

@Builder
public record ExecutionModelDescriptor(
        ExecutionModelType value,
        String label,
        String description,
        List<StrategyParameterDescriptor> parameters
) {
}
