package com.trading.marketdata.model;

public record OrderBookEvent(
    String exchange,
    String symbol,
    double bestBidPrice,
    double bestAskPrice,
    double bestBidQuantity,
    double bestAskQuantity,
    long timestamp
) {
}
