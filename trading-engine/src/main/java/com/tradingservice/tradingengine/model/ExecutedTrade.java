package com.tradingservice.tradingengine.model;

import lombok.Builder;

@Builder
public record ExecutedTrade(
        long timestamp,
        TradeAction action,
        PositionSide positionSide,
        TradeExecutionType executionType,
        double price,
        double quantity,
        double notional,
        double realizedPnl,
        double cashBalanceAfter,
        double assetQuantityAfter
) {
}
