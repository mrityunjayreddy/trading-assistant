package com.trading.marketdata.normalizer;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.trading.marketdata.model.KlineEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Normalizes Binance combined-stream kline messages into {@link KlineEvent}.
 *
 * Expected input (combined-stream wrapper):
 * <pre>
 * {
 *   "stream": "btcusdt@kline_1m",
 *   "data": {
 *     "e": "kline",
 *     "s": "BTCUSDT",
 *     "k": {
 *       "t": 1638747600000,  "T": 1638747659999,
 *       "s": "BTCUSDT",     "i": "1m",
 *       "o": "58000.00",    "h": "58200.00",
 *       "l": "57900.00",    "c": "58100.00",
 *       "v": "100.5",       "n": 1234,
 *       "x": true
 *     }
 *   }
 * }
 * </pre>
 */
@Component
public class BinanceKlineNormalizer {

    private static final String EXCHANGE = "BINANCE";

    private final ObjectMapper objectMapper;

    public BinanceKlineNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public KlineEvent normalize(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            // Combined-stream messages wrap the event inside "data"
            JsonNode data = root.has("data") ? root.get("data") : root;
            JsonNode k = data.get("k");

            if (k == null) {
                throw new IllegalArgumentException("Missing 'k' kline node in payload: " + payload);
            }

            return new KlineEvent(
                EXCHANGE,
                k.get("s").asText().toUpperCase(Locale.ROOT),
                k.get("i").asText(),
                Instant.ofEpochMilli(k.get("t").asLong()),
                Instant.ofEpochMilli(k.get("T").asLong()),
                new BigDecimal(k.get("o").asText()),
                new BigDecimal(k.get("h").asText()),
                new BigDecimal(k.get("l").asText()),
                new BigDecimal(k.get("c").asText()),
                new BigDecimal(k.get("v").asText()),
                k.get("n").asLong(),
                k.get("x").asBoolean()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Binance kline payload", e);
        }
    }
}