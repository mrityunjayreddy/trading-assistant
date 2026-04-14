package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.TradeSignal;
import java.util.List;

public interface Strategy {

    List<TradeSignal> generateSignals(List<Kline> klines);
}
