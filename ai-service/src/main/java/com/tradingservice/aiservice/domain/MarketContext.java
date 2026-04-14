package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Captures current market regime for context-aware strategy generation and memory retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketContext {

    public enum Trend { UP, DOWN, SIDEWAYS }
    public enum Volatility { LOW, MEDIUM, HIGH }
    public enum Session { ASIA, EUROPE, US }

    private String symbol;
    private String interval;
    private Trend trend;
    private Volatility volatility;
    private Session session;
    private Instant analyzedAt;

    public static MarketContext build(String symbol, String interval) {
        return MarketContext.builder()
                .symbol(symbol)
                .interval(interval)
                .trend(Trend.SIDEWAYS)
                .volatility(Volatility.MEDIUM)
                .session(Session.ASIA)
                .analyzedAt(Instant.now())
                .build();
    }
}
