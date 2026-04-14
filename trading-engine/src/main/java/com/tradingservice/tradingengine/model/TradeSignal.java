package com.tradingservice.tradingengine.model;

import lombok.Builder;

@Builder
public record TradeSignal(
        long timestamp,
        TradeAction action,
        double price
) {
}
