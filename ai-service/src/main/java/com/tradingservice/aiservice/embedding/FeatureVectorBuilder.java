package com.tradingservice.aiservice.embedding;

import com.tradingservice.aiservice.domain.BacktestResultRecord;
import com.tradingservice.aiservice.domain.MarketContext;
import com.tradingservice.aiservice.domain.StrategyRecord;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Converts a strategy + backtest result into a float[1536] embedding vector.
 *
 * DESIGN NOTE: This is a deterministic heuristic embedding for prototyping.
 * In production, replace with sentence-transformers or Anthropic embeddings
 * for true semantic similarity.
 *
 * Vector layout:
 *   Segment 1 (0-99):    Indicator features (one-hot presence, normalized periods)
 *   Segment 2 (100-199): Performance features (normalized 0-1)
 *   Segment 3 (200-299): Market context features (trend, volatility, session)
 *   Segment 4 (300-1535): DSL token frequency (tf-idf style)
 */
@Component
public class FeatureVectorBuilder {

    // Known indicator types for one-hot encoding (Segment 1)
    private static final List<String> KNOWN_INDICATOR_TYPES = List.of(
        "RSI", "EMA", "SMA", "MACD", "ATR", "BB", "STOCH", "VOLUME"
    );

    // Known DSL tokens for frequency counting (Segment 4)
    private static final List<String> KNOWN_DSL_TOKENS = List.of(
        "RSI_14", "EMA_9", "EMA_21", "MACD", "ATR_14",
        "BB_LOWER", "BB_UPPER", "BB_MIDDLE",
        "AND", "OR", "NOT",
        "<", ">", "<=", ">=", "==", "!=",
        "CLOSE", "OPEN", "VOLUME"
    );

    private static final int VECTOR_SIZE = 1536;

    /**
     * Build complete 1536-dim embedding from strategy and backtest result.
     */
    public float[] buildVector(StrategyRecord strategy, BacktestResultRecord result, MarketContext context) {
        float[] vector = new float[VECTOR_SIZE];

        // Segment 1: Indicator features (0-99)
        encodeIndicatorFeatures(vector, strategy);

        // Segment 2: Performance features (100-199)
        encodePerformanceFeatures(vector, result);

        // Segment 3: Market context features (200-299)
        if (context != null) {
            encodeMarketContextFeatures(vector, context);
        }

        // Segment 4: DSL token frequency (300-1535)
        encodeDslTokenFrequency(vector, strategy);

        return vector;
    }

    /**
     * Build query vector from market context only (for retrieval).
     * Uses only segments 2 and 3 - performance + market context.
     */
    public float[] buildQueryVector(MarketContext context, Double targetSharpe, Double targetDrawdown) {
        float[] vector = new float[VECTOR_SIZE];

        // Segment 2: Performance features (100-199) - use target values if provided
        if (targetSharpe != null) {
            // sharpeRatio normalized: (sharpe + 3) / 6.0
            vector[100] = normalizeSharpe(targetSharpe);
        }
        // Other performance features left as zero for query

        // Segment 3: Market context features (200-299)
        if (context != null) {
            encodeMarketContextFeatures(vector, context);
        }

        return vector;
    }

    /**
     * Segment 1 (indices 0-99): Indicator features
     *   One-hot presence: indices 0-7 (1.0 if used, 0.0 if not)
     *   Normalized period: indices 8-15 (period/100.0, 0 if not used)
     *   Remaining 84 slots: zero-padded
     */
    private void encodeIndicatorFeatures(float[] vector, StrategyRecord strategy) {
        List<Map<String, Object>> indicators = strategy.getIndicators();
        Set<String> usedTypes = new HashSet<>();
        Map<String, Integer> maxPeriods = new HashMap<>();

        // Extract indicator types and periods
        for (Map<String, Object> indicator : indicators) {
            String type = (String) indicator.get("type");
            if (type == null) continue;

            // Normalize type (e.g., "BOLLINGER_BANDS" -> "BB")
            String normalizedType = normalizeIndicatorType(type);
            if (normalizedType != null) {
                usedTypes.add(normalizedType);

                // Extract period if present
                Map<String, Object> params = (Map<String, Object>) indicator.get("params");
                if (params != null && params.containsKey("period")) {
                    Object periodObj = params.get("period");
                    if (periodObj instanceof Number) {
                        int period = ((Number) periodObj).intValue();
                        maxPeriods.merge(normalizedType, period, Math::max);
                    }
                }
            }
        }

        // One-hot presence (indices 0-7)
        for (int i = 0; i < KNOWN_INDICATOR_TYPES.size(); i++) {
            if (usedTypes.contains(KNOWN_INDICATOR_TYPES.get(i))) {
                vector[i] = 1.0f;
            }
        }

        // Normalized periods (indices 8-15)
        for (int i = 0; i < KNOWN_INDICATOR_TYPES.size(); i++) {
            String type = KNOWN_INDICATOR_TYPES.get(i);
            if (maxPeriods.containsKey(type)) {
                vector[8 + i] = maxPeriods.get(type) / 100.0f;
            }
        }
        // Remaining indices 16-99 stay zero
    }

    /**
     * Segment 2 (indices 100-199): Performance features (all normalized 0-1)
     *   [100] sharpeRatio: (sharpe + 3) / 6.0 (maps -3..3 to 0..1)
     *   [101] winRate / 100.0
     *   [102] 1.0 - (maxDrawdown / 100.0)
     *   [103] min(totalTrades, 500) / 500.0
     *   [104] sigmoid(totalPnl / 10000.0)
     *   Remaining: zero-padded
     */
    private void encodePerformanceFeatures(float[] vector, BacktestResultRecord result) {
        // Sharpe ratio normalized
        if (result.getSharpeRatio() != null) {
            vector[100] = normalizeSharpe(result.getSharpeRatio());
        }

        // Win rate normalized
        if (result.getWinRate() != null) {
            vector[101] = (float) (result.getWinRate() / 100.0);
        }

        // Drawdown inverse normalized
        if (result.getMaxDrawdown() != null) {
            vector[102] = (float) (1.0 - (result.getMaxDrawdown() / 100.0));
        }

        // Trade count normalized
        if (result.getTotalTrades() != null) {
            int trades = Math.min(result.getTotalTrades(), 500);
            vector[103] = trades / 500.0f;
        }

        // PnL sigmoid normalized
        if (result.getTotalPnl() != null) {
            vector[104] = (float) sigmoid(result.getTotalPnl() / 10000.0);
        }
        // Remaining indices 105-199 stay zero
    }

    /**
     * Segment 3 (indices 200-299): Market context features
     *   [200] trend: UP=1.0, DOWN=0.0, SIDEWAYS=0.5
     *   [201] volatility: LOW=0.0, MEDIUM=0.5, HIGH=1.0
     *   [202] session: ASIA=0.0, EUROPE=0.5, US=1.0
     *   Remaining: zero-padded
     */
    private void encodeMarketContextFeatures(float[] vector, MarketContext context) {
        // Trend
        if (context.getTrend() != null) {
            vector[200] = switch (context.getTrend()) {
                case UP -> 1.0f;
                case DOWN -> 0.0f;
                case SIDEWAYS -> 0.5f;
            };
        }

        // Volatility
        if (context.getVolatility() != null) {
            vector[201] = switch (context.getVolatility()) {
                case LOW -> 0.0f;
                case MEDIUM -> 0.5f;
                case HIGH -> 1.0f;
            };
        }

        // Session
        if (context.getSession() != null) {
            vector[202] = switch (context.getSession()) {
                case ASIA -> 0.0f;
                case EUROPE -> 0.5f;
                case US -> 1.0f;
            };
        }
        // Remaining indices 203-299 stay zero
    }

    /**
     * Segment 4 (indices 300-1535): DSL token frequency
     * Tokenize (entry + " " + exit) expression by spaces
     * For each of 20 known tokens: count / total (indices 300-319)
     * Remaining: zero-padded to 1536
     */
    private void encodeDslTokenFrequency(float[] vector, StrategyRecord strategy) {
        String entry = strategy.getEntry();
        String exit = strategy.getExit();
        String combined = (entry + " " + exit).toLowerCase(Locale.ROOT);

        // Tokenize by spaces and common operators
        List<String> tokens = tokenize(combined);
        int totalTokens = tokens.size();

        if (totalTokens == 0) return;

        // Count known tokens
        Map<String, Integer> tokenCounts = new HashMap<>();
        for (String token : tokens) {
            String normalized = normalizeToken(token);
            if (normalized != null) {
                tokenCounts.merge(normalized, 1, Integer::sum);
            }
        }

        // Encode tf-idf style frequency (indices 300-319)
        for (int i = 0; i < KNOWN_DSL_TOKENS.size(); i++) {
            String dslToken = KNOWN_DSL_TOKENS.get(i).toLowerCase(Locale.ROOT);
            int count = tokenCounts.getOrDefault(dslToken, 0);
            vector[300 + i] = count / (float) totalTokens;
        }
        // Remaining indices 320-1535 stay zero
    }

    /**
     * Tokenize a DSL expression into individual tokens.
     */
    private List<String> tokenize(String expression) {
        List<String> tokens = new ArrayList<>();

        // Split by spaces first
        String[] spaceSplit = expression.split("\\s+");
        for (String part : spaceSplit) {
            if (part.isEmpty()) continue;

            // Further split by operators
            String remaining = part;

            // Handle comparison operators first (longer matches)
            String[] operators = {">=", "<=", "==", "!=", ">", "<"};
            for (String op : operators) {
                if (remaining.contains(op)) {
                    String[] opSplit = remaining.split("(?<=" + Pattern.quote(op) + ")|(?=" + Pattern.quote(op) + ")");
                    for (String s : opSplit) {
                        if (!s.isEmpty() && !s.equals(op)) {
                            tokens.add(s);
                        }
                    }
                    // Add the operator itself
                    if (remaining.contains(op)) {
                        tokens.add(op);
                        remaining = remaining.replace(op, " ").trim();
                    }
                }
            }

            // Add remaining alphanumeric tokens
            if (!remaining.matches(".*[><=!].*") && !remaining.isEmpty()) {
                tokens.add(remaining);
            }
        }

        return tokens.stream()
                .filter(t -> !t.isEmpty() && !t.matches("\\s+"))
                .toList();
    }

    /**
     * Normalize a token to match known DSL tokens.
     */
    private String normalizeToken(String token) {
        String upper = token.toUpperCase(Locale.ROOT);

        // Direct matches
        for (String known : KNOWN_DSL_TOKENS) {
            if (known.equalsIgnoreCase(token)) {
                return known;
            }
        }

        // Handle RSI_N patterns
        if (upper.startsWith("RSI")) {
            return "RSI_14"; // Normalize all RSI to RSI_14 for simplicity
        }

        // Handle EMA_N patterns
        if (upper.startsWith("EMA")) {
            if (upper.contains("9")) return "EMA_9";
            if (upper.contains("21")) return "EMA_21";
            return "EMA_9"; // Default
        }

        // Handle SMA_N patterns
        if (upper.startsWith("SMA")) {
            return "SMA"; // Map to generic
        }

        // Handle MACD
        if (upper.contains("MACD")) {
            return "MACD";
        }

        // Handle ATR_N patterns
        if (upper.startsWith("ATR")) {
            return "ATR_14";
        }

        // Handle Bollinger Bands
        if (upper.contains("LOWER") || upper.contains("BOTTOM")) {
            return "BB_LOWER";
        }
        if (upper.contains("UPPER") || upper.contains("TOP")) {
            return "BB_UPPER";
        }
        if (upper.contains("MIDDLE") || upper.contains("MID")) {
            return "BB_MIDDLE";
        }
        if (upper.contains("BB") || upper.contains("BOLLINGER")) {
            return "BB_MIDDLE"; // Default
        }

        // Handle Stochastic
        if (upper.contains("STOCH") || upper.contains("K") || upper.contains("D")) {
            return "STOCH_K";
        }

        // Handle Volume
        if (upper.contains("VOL") || upper.contains("VOLUME")) {
            return "VOLUME";
        }

        // Handle Close/Open
        if (upper.equals("CLOSE") || upper.equals("C")) {
            return "CLOSE";
        }
        if (upper.equals("OPEN") || upper.equals("O")) {
            return "OPEN";
        }

        return null;
    }

    /**
     * Normalize indicator type to known types.
     */
    private String normalizeIndicatorType(String type) {
        String upper = type.toUpperCase(Locale.ROOT);

        if (upper.contains("RSI")) return "RSI";
        if (upper.equals("EMA") || upper.contains("EXPONENTIAL")) return "EMA";
        if (upper.equals("SMA") || upper.contains("SIMPLE")) return "SMA";
        if (upper.contains("MACD")) return "MACD";
        if (upper.contains("ATR")) return "ATR";
        if (upper.contains("BOLLINGER") || upper.equals("BB")) return "BB";
        if (upper.contains("STOCH") || upper.contains("STOCHASTIC")) return "STOCH";
        if (upper.contains("VOLUME") || upper.equals("OBV") || upper.equals("VWAP")) return "VOLUME";

        return null;
    }

    /**
     * Normalize Sharpe ratio to 0-1 range.
     * Maps -3..3 to 0..1
     */
    private float normalizeSharpe(double sharpe) {
        return (float) ((sharpe + 3.0) / 6.0);
    }

    /**
     * Sigmoid function for PnL normalization.
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

}
