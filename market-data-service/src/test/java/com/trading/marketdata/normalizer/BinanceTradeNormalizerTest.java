package com.trading.marketdata.normalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.model.TradeEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceTradeNormalizerTest {

    private final BinanceTradeNormalizer normalizer = new BinanceTradeNormalizer(new ObjectMapper());

    @Test
    void shouldNormalizeBuyTrade() {
        String payload = """
            {
              "e": "trade",
              "E": 1710000000,
              "s": "BTCUSDT",
              "t": 123456,
              "p": "67321.50",
              "q": "0.12",
              "m": false
            }
            """;

        TradeEvent tradeEvent = normalizer.normalize(payload);

        assertThat(tradeEvent.exchange()).isEqualTo("BINANCE");
        assertThat(tradeEvent.symbol()).isEqualTo("BTCUSDT");
        assertThat(tradeEvent.price()).isEqualTo(67321.50d);
        assertThat(tradeEvent.quantity()).isEqualTo(0.12d);
        assertThat(tradeEvent.side()).isEqualTo("BUY");
        assertThat(tradeEvent.tradeId()).isEqualTo(123456L);
        assertThat(tradeEvent.timestamp()).isEqualTo(1710000000L);
    }

    @Test
    void shouldNormalizeSellTrade() {
        String payload = """
            {
              "e": "trade",
              "E": 1710000001,
              "s": "ETHUSDT",
              "t": 654321,
              "p": "3500.10",
              "q": "5.50",
              "m": true
            }
            """;

        TradeEvent tradeEvent = normalizer.normalize(payload);

        assertThat(tradeEvent.side()).isEqualTo("SELL");
        assertThat(tradeEvent.symbol()).isEqualTo("ETHUSDT");
    }
}
