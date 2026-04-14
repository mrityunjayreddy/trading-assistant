package com.tradingservice.tradingengine.dto;

import lombok.Builder;

@Builder
public record StrategyParameterDescriptor(
        String name,
        String label,
        String type,
        Object defaultValue,
        Number minValue,
        Number maxValue,
        boolean required,
        String description
) {
}
