package com.tradingservice.aiservice.analysis;

import com.tradingservice.aiservice.domain.MarketContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Analyzes recent market data to determine current market regime.
 *
 * Reads last 100 bars from market_data table for the given symbol+interval:
 * - trend: EMA(20) slope over last 10 bars
 *   - slope > 0.001 → UP
 *   - slope < -0.001 → DOWN
 *   - else → SIDEWAYS
 * - volatility: ATR(14) / close_price * 100
 *   - < 1.0% → LOW
 *   - 1.0-3.0% → MEDIUM
 *   - > 3.0% → HIGH
 * - session: based on UTC hour
 *   - 0-7 → ASIA
 *   - 8-15 → EUROPE
 *   - 16-23 → US
 */
@Component
public class MarketContextAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(MarketContextAnalyzer.class);

    private final JdbcTemplate jdbcTemplate;

    public MarketContextAnalyzer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Analyze current market context for the given symbol and interval.
     */
    public MarketContext analyze(String symbol, String interval) {
        log.debug("Analyzing market context for {} {}", symbol, interval);

        // Fetch last 100 bars
        List<Bar> bars = fetchBars(symbol, interval, 100);
        if (bars.isEmpty()) {
            log.warn("No market data found for {} {}, returning unknown context", symbol, interval);
            return MarketContext.build(symbol, interval);
        }

        // Compute trend from EMA(20) slope over last 10 bars
        MarketContext.Trend trend = computeTrend(bars);

        // Compute volatility from ATR(14)
        MarketContext.Volatility volatility = computeVolatility(bars);

        // Determine current session from UTC time
        MarketContext.Session session = computeSession(Instant.now());

        MarketContext context = MarketContext.builder()
                .symbol(symbol)
                .interval(interval)
                .trend(trend)
                .volatility(volatility)
                .session(session)
                .analyzedAt(Instant.now())
                .build();

        log.info("Market context for {} {}: trend={}, volatility={}, session={}",
                symbol, interval, trend, volatility, session);

        return context;
    }

    /**
     * Fetch last N bars from market_data table.
     */
    private List<Bar> fetchBars(String symbol, String interval, int limit) {
        String sql = """
            SELECT open_time, open, high, low, close, volume
            FROM market_data
            WHERE exchange = 'binance'
              AND symbol = ?
              AND interval = ?
            ORDER BY open_time DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new Bar(
                rs.getTimestamp("open_time").toInstant(),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getDouble("volume")
        ), symbol, interval, limit);
    }

    /**
     * Compute trend from EMA(20) slope over last 10 bars.
     */
    private MarketContext.Trend computeTrend(List<Bar> bars) {
        // Need 30 bars: 20 for SMA seed + 10 for the EMA window we measure slope over
        if (bars.size() < 30) {
            log.debug("Insufficient bars ({}) for trend calculation, defaulting to SIDEWAYS", bars.size());
            return MarketContext.Trend.SIDEWAYS;
        }

        // bars is DESC ordered: bars[0] = newest, bars[N-1] = oldest.
        // EMA window: bars[9..0] (10 most-recent bars).
        // SMA seed: bars[29..10] (20 bars older than the EMA window).
        double multiplier = 2.0 / (20 + 1);  // EMA multiplier
        double[] emaValues = new double[10];

        // Compute SMA seed from bars[29..10] (oldest-first to get correct average)
        double ema = 0;
        for (int i = 29; i >= 10; i--) {
            ema += bars.get(i).close;
        }
        ema = ema / 20;

        // Compute EMA for bars[9..0] in chronological order (oldest first)
        // emaValues[0] = EMA 9 bars ago, emaValues[9] = EMA at current bar
        for (int i = 9; i >= 0; i--) {
            Bar bar = bars.get(i);
            ema = (bar.close * multiplier) + (ema * (1 - multiplier));
            emaValues[9 - i] = ema;
        }

        // Compute slope: (last - first) / first
        double slope = (emaValues[9] - emaValues[0]) / emaValues[0];

        log.trace("EMA slope: {} (first={}, last={})",
                String.format("%.6f", slope), String.format("%.4f", emaValues[0]), String.format("%.4f", emaValues[9]));

        if (slope > 0.001) {
            return MarketContext.Trend.UP;
        } else if (slope < -0.001) {
            return MarketContext.Trend.DOWN;
        } else {
            return MarketContext.Trend.SIDEWAYS;
        }
    }

    /**
     * Compute volatility from ATR(14) / close_price * 100.
     */
    private MarketContext.Volatility computeVolatility(List<Bar> bars) {
        if (bars.size() < 15) {
            log.debug("Insufficient bars ({}) for volatility calculation, defaulting to MEDIUM", bars.size());
            return MarketContext.Volatility.MEDIUM;
        }

        // Compute ATR(14) using most recent 15 bars
        double[] trValues = new double[14];
        for (int i = 0; i < 14; i++) {
            Bar current = bars.get(i);
            Bar previous = bars.get(i + 1);

            double highLow = current.high - current.low;
            double highClose = Math.abs(current.high - previous.close);
            double lowClose = Math.abs(current.low - previous.close);

            trValues[i] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Simple average for ATR
        double atr = 0;
        for (double tr : trValues) {
            atr += tr;
        }
        atr = atr / 14;

        // Normalize by current close price
        double currentClose = bars.get(0).close;
        double volatilityPercent = (atr / currentClose) * 100.0;

        log.trace("ATR(14)={}, close={}, volatility={}%",
                String.format("%.6f", atr), String.format("%.2f", currentClose), String.format("%.2f", volatilityPercent));

        if (volatilityPercent < 1.0) {
            return MarketContext.Volatility.LOW;
        } else if (volatilityPercent <= 3.0) {
            return MarketContext.Volatility.MEDIUM;
        } else {
            return MarketContext.Volatility.HIGH;
        }
    }

    /**
     * Determine trading session from UTC hour.
     */
    private MarketContext.Session computeSession(Instant instant) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        int hour = utc.getHour();

        // Session hours (UTC):
        // ASIA: 00:00 - 08:00 (Tokyo, Hong Kong, Singapore)
        // EUROPE: 08:00 - 16:00 (London, Frankfurt)
        // US: 16:00 - 24:00 (New York, Chicago)
        if (hour >= 0 && hour < 8) {
            return MarketContext.Session.ASIA;
        } else if (hour >= 8 && hour < 16) {
            return MarketContext.Session.EUROPE;
        } else {
            return MarketContext.Session.US;
        }
    }

    /**
     * Simple bar DTO.
     */
    private record Bar(
            Instant openTime,
            double open,
            double high,
            double low,
            double close,
            double volume
    ) {}
}
