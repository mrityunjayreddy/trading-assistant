package com.trading.marketdata.model;

public record TradeEvent(
    String exchange,
    String symbol,
    double price,
    double quantity,
    String side,
    long tradeId,
    long timestamp
) {
}
