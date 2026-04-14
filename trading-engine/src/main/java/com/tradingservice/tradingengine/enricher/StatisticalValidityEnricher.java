package com.tradingservice.tradingengine.enricher;

import com.tradingservice.tradingengine.model.EquityPoint;
import com.tradingservice.tradingengine.model.ExecutedTrade;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.model.TradeExecutionType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adds statistical enrichment fields to a {@link SimulationResult} without
 * modifying any existing fields.
 *
 * <p>Computes:
 * <ul>
 *   <li><b>winRate</b>       — % of closed trades with realizedPnl &gt; 0</li>
 *   <li><b>maxDrawdown</b>   — maximum peak-to-trough decline from equity curve (%)</li>
 *   <li><b>annualizedSharpe</b> — mean(returns) / stdDev(returns) × √252 from equity curve</li>
 *   <li><b>isStatisticallyValid</b> — tradesCount ≥ 50</li>
 *   <li><b>validationNote</b>       — human-readable note</li>
 * </ul>
 * </p>
 *
 * This bean is purely additive — it never modifies the seven original fields.
 */
@Component
public class StatisticalValidityEnricher {

    private static final Logger log = LoggerFactory.getLogger(StatisticalValidityEnricher.class);
    private static final int MIN_TRADES_FOR_VALIDITY = 50;
    private static final double ANNUALIZATION_FACTOR = Math.sqrt(252.0);

    public Mono<SimulationResult> enrich(SimulationResult result) {
        return Mono.fromCallable(() -> doEnrich(result))
                   .doOnError(e -> log.warn("Enrichment failed, returning un-enriched result: {}", e.getMessage()));
    }

    // -------------------------------------------------------------------------

    private SimulationResult doEnrich(SimulationResult result) {
        double winRate        = computeWinRate(result.trades());
        double maxDrawdown    = computeMaxDrawdown(result.equityCurve());
        double annualizedSharpe = computeAnnualizedSharpe(result.equityCurve());

        int tradeCount = result.tradesCount();
        boolean isValid = tradeCount >= MIN_TRADES_FOR_VALIDITY;

        long days = estimateDays(result.equityCurve());
        String note = isValid
            ? "Valid — %d trades over %d day period".formatted(tradeCount, days)
            : "Insufficient trades (%d) — minimum %d required for statistical validity"
                    .formatted(tradeCount, MIN_TRADES_FOR_VALIDITY);

        return SimulationResult.builder()
                // ---- original fields (copied unchanged) ----
                .initialBalance(result.initialBalance())
                .finalBalance(result.finalBalance())
                .totalReturn(result.totalReturn())
                .tradesCount(result.tradesCount())
                .candles(result.candles())
                .trades(result.trades())
                .equityCurve(result.equityCurve())
                // ---- new enrichment fields ----
                .winRate(round(winRate))
                .maxDrawdown(round(maxDrawdown))
                .annualizedSharpe(round(annualizedSharpe))
                .isStatisticallyValid(isValid)
                .validationNote(note)
                .build();
    }

    // -------------------------------------------------------------------------
    // Win rate
    // -------------------------------------------------------------------------

    /**
     * Win rate = (profitable close trades) / (total close trades) × 100.
     * Open trades (zero realizedPnl) are excluded.
     */
    private double computeWinRate(List<ExecutedTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        List<ExecutedTrade> closeTrades = trades.stream()
                .filter(t -> t.executionType() == TradeExecutionType.CLOSE_LONG
                          || t.executionType() == TradeExecutionType.CLOSE_SHORT)
                .toList();

        if (closeTrades.isEmpty()) {
            return 0.0;
        }

        long winners = closeTrades.stream()
                .filter(t -> t.realizedPnl() > 0.0)
                .count();

        return ((double) winners / closeTrades.size()) * 100.0;
    }

    // -------------------------------------------------------------------------
    // Max drawdown
    // -------------------------------------------------------------------------

    /**
     * Max drawdown = maximum peak-to-trough percentage decline in equity.
     */
    private double computeMaxDrawdown(List<EquityPoint> equityCurve) {
        if (equityCurve == null || equityCurve.size() < 2) {
            return 0.0;
        }

        double peak = equityCurve.getFirst().equity();
        double maxDd = 0.0;

        for (EquityPoint point : equityCurve) {
            double equity = point.equity();
            if (equity > peak) {
                peak = equity;
            }
            if (peak > 0) {
                double drawdown = (peak - equity) / peak * 100.0;
                if (drawdown > maxDd) {
                    maxDd = drawdown;
                }
            }
        }
        return maxDd;
    }

    // -------------------------------------------------------------------------
    // Annualized Sharpe
    // -------------------------------------------------------------------------

    /**
     * Annualized Sharpe = mean(bar returns) / stdDev(bar returns) × √252.
     * Returns 0 if variance is zero or insufficient data.
     */
    private double computeAnnualizedSharpe(List<EquityPoint> equityCurve) {
        if (equityCurve == null || equityCurve.size() < 3) {
            return 0.0;
        }

        double[] returns = new double[equityCurve.size() - 1];
        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = equityCurve.get(i - 1).equity();
            double curr = equityCurve.get(i).equity();
            returns[i - 1] = prev > 0 ? (curr - prev) / prev : 0.0;
        }

        double mean   = mean(returns);
        double stdDev = stdDev(returns, mean);

        if (stdDev == 0.0 || Double.isNaN(stdDev)) {
            return 0.0;
        }

        double sharpe = (mean / stdDev) * ANNUALIZATION_FACTOR;
        return Double.isFinite(sharpe) ? sharpe : 0.0;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double stdDev(double[] values, double mean) {
        double sumSq = 0.0;
        for (double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / values.length);
    }

    private long estimateDays(List<EquityPoint> equityCurve) {
        if (equityCurve == null || equityCurve.size() < 2) {
            return 0L;
        }
        long first = equityCurve.getFirst().timestamp();
        long last  = equityCurve.getLast().timestamp();
        return (last - first) / (1000L * 60 * 60 * 24);
    }

    private double round(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}