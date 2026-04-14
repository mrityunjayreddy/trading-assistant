package com.trading.marketdata.normalizer;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Locale;

import com.trading.marketdata.model.TradeEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Component
public class BinanceTradeNormalizer {

    private static final String EXCHANGE = "BINANCE";

    private final ObjectMapper objectMapper;

    public BinanceTradeNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TradeEvent normalize(String payload) {
        try {
            BinanceTradeMessage message = objectMapper.readValue(payload, BinanceTradeMessage.class);
            String side = message.makerBuyer() ? "SELL" : "BUY";

            return new TradeEvent(
                EXCHANGE,
                message.symbol().toUpperCase(Locale.ROOT),
                Double.parseDouble(message.price()),
                Double.parseDouble(message.quantity()),
                side,
                message.tradeId(),
                message.eventTime()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to normalize Binance trade payload", exception);
        }
    }

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    private record BinanceTradeMessage(
        String e,
        long E,
        String s,
        long t,
        String p,
        String q,
        boolean m
    ) {
        long eventTime() {
            return E;
        }

        String symbol() {
            return s;
        }

        long tradeId() {
            return t;
        }

        String price() {
            return p;
        }

        String quantity() {
            return q;
        }

        boolean makerBuyer() {
            return m;
        }
    }
}
