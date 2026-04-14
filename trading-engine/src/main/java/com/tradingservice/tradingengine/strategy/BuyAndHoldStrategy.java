package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.TradeAction;
import com.tradingservice.tradingengine.model.TradeSignal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyAndHoldStrategy implements Strategy {

    public static final String NAME = "BUY_AND_HOLD";

    @Override
    public List<TradeSignal> generateSignals(List<Kline> klines) {
        if (klines.isEmpty()) {
            return List.of();
        }

        List<TradeSignal> signals = java.util.stream.IntStream.range(0, klines.size())
                .mapToObj(index -> {
                    Kline kline = klines.get(index);
                    TradeAction action = index == 0 ? TradeAction.BUY : TradeAction.HOLD;
                    return TradeSignal.builder()
                            .timestamp(kline.openTime())
                            .price(kline.close())
                            .action(action)
                            .build();
                })
                .toList();

        log.info("Generated {} signals using {}", signals.size(), NAME);
        return signals;
    }
}
