package com.tradingservice.tradingassistantbackend.model;

public record Candle(
        String symbol,
        long openTime,
        long closeTime,
        double open,
        double high,
        double low,
        double close,
        double volume,
        boolean closed,
        String interval
) {
}
