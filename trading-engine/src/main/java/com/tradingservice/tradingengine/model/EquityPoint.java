package com.tradingservice.tradingengine.model;

import lombok.Builder;

@Builder
public record EquityPoint(
        long timestamp,
        double equity,
        double closePrice
) {
}
