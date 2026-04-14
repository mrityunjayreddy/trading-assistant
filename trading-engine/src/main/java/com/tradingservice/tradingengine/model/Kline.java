package com.tradingservice.tradingengine.model;

import lombok.Builder;

@Builder
public record Kline(
        long openTime,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
