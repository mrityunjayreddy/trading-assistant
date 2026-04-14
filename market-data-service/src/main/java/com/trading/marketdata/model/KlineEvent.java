package com.trading.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;

public record KlineEvent(
    String exchange,
    String symbol,
    String interval,
    Instant openTime,
    Instant closeTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    long tradeCount,
    boolean isClosed
) {
}