package com.tradingservice.strategyservice.service;

import com.tradingservice.strategyservice.domain.IndicatorConfig;
import com.tradingservice.strategyservice.domain.RiskConfig;
import com.tradingservice.strategyservice.domain.StrategyDSL;
import com.tradingservice.strategyservice.entity.StrategyRecord;
import com.tradingservice.strategyservice.exception.ValidationException;
import com.tradingservice.strategyservice.repository.StrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Five-check validation chain for incoming {@link StrategyDSL} objects.
 *
 * <ol>
 *   <li>Required fields: name, source, entry, exit must be non-blank; indicators non-empty</li>
 *   <li>Risk bounds: stopLossPct/takeProfitPct/positionSizePct in (0, 100]; if present</li>
 *   <li>Indicator types: every indicator.type must be in the supported set</li>
 *   <li>Dry-run: sends the DSL to trading-engine {@code /api/v1/simulate/dsl} with a short
 *       window to confirm it parses and executes without error</li>
 *   <li>Diversity: Levenshtein distance from every existing active strategy's entry expression
 *       must be ≥ 15 — rejects near-duplicate submissions</li>
 * </ol>
 *
 * Throws {@link ValidationException} if any check fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyValidator {

    private static final Set<String> SUPPORTED_INDICATOR_TYPES = Set.of(
            "SMA", "EMA", "RSI", "MACD", "BOLLINGER_BANDS", "ATR",
            "STOCHASTIC", "VWAP", "OBV", "MOMENTUM", "CCI", "ADX"
    );

    private static final int MIN_LEVENSHTEIN_DISTANCE = 15;

    private final StrategyRepository strategyRepository;
    private final RestClient tradingEngineClient;

    // -------------------------------------------------------------------------

    public void validate(StrategyDSL dsl) {
        List<String> errors = new ArrayList<>();

        checkRequiredFields(dsl, errors);
        checkRiskBounds(dsl, errors);
        checkIndicatorTypes(dsl, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // Dry-run only if structural checks pass — avoids unnecessary HTTP calls
        checkDryRun(dsl, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        checkDiversity(dsl, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    // -------------------------------------------------------------------------
    // Check 1: Required fields
    // -------------------------------------------------------------------------

    private void checkRequiredFields(StrategyDSL dsl, List<String> errors) {
        if (blank(dsl.name())) {
            errors.add("name is required");
        }
        if (blank(dsl.source())) {
            errors.add("source is required");
        } else if (!Set.of("BUILTIN", "USER", "LLM", "EVOLVED").contains(dsl.source())) {
            errors.add("source must be one of: BUILTIN, USER, LLM, EVOLVED");
        }
        if (blank(dsl.entry())) {
            errors.add("entry expression is required");
        }
        if (blank(dsl.exit())) {
            errors.add("exit expression is required");
        }
        if (dsl.indicators() == null || dsl.indicators().isEmpty()) {
            errors.add("at least one indicator is required");
        } else {
            for (int i = 0; i < dsl.indicators().size(); i++) {
                IndicatorConfig ic = dsl.indicators().get(i);
                if (blank(ic.id())) {
                    errors.add("indicators[" + i + "].id is required");
                }
                if (blank(ic.type())) {
                    errors.add("indicators[" + i + "].type is required");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Check 2: Risk bounds
    // -------------------------------------------------------------------------

    private void checkRiskBounds(StrategyDSL dsl, List<String> errors) {
        RiskConfig risk = dsl.risk();
        if (risk == null) return;

        if (risk.stopLossPct() != null) {
            if (risk.stopLossPct() <= 0 || risk.stopLossPct() > 100) {
                errors.add("risk.stopLossPct must be in (0, 100]");
            }
        }
        if (risk.takeProfitPct() != null) {
            if (risk.takeProfitPct() <= 0 || risk.takeProfitPct() > 100) {
                errors.add("risk.takeProfitPct must be in (0, 100]");
            }
        }
        if (risk.positionSizePct() != null) {
            if (risk.positionSizePct() <= 0 || risk.positionSizePct() > 100) {
                errors.add("risk.positionSizePct must be in (0, 100]");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Check 3: Indicator types
    // -------------------------------------------------------------------------

    private void checkIndicatorTypes(StrategyDSL dsl, List<String> errors) {
        if (dsl.indicators() == null) return;
        for (IndicatorConfig ic : dsl.indicators()) {
            if (ic.type() != null && !SUPPORTED_INDICATOR_TYPES.contains(ic.type().toUpperCase())) {
                errors.add("unsupported indicator type: " + ic.type()
                        + " — supported: " + SUPPORTED_INDICATOR_TYPES);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Check 4: Dry-run against trading-engine
    // -------------------------------------------------------------------------

    private void checkDryRun(StrategyDSL dsl, List<String> errors) {
        // 90 days window ending now — minimal but enough to trigger execution
        long endTime   = Instant.now().toEpochMilli();
        long startTime = endTime - 90L * 24 * 60 * 60 * 1000;

        Map<String, Object> dryRunRequest = Map.of(
                "symbol",   "BTCUSDT",
                "interval", "1d",
                "dsl",      dsl,
                "range",    Map.of("startTime", startTime, "endTime", endTime)
        );

        try {
            tradingEngineClient.post()
                    .uri("/api/v1/simulate/dsl")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dryRunRequest)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Dry-run validation failed for strategy '{}': {}", dsl.name(), e.getMessage());
            errors.add("dry-run execution failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Check 5: Diversity (Levenshtein ≥ 15 from all active strategies)
    // -------------------------------------------------------------------------

    private void checkDiversity(StrategyDSL dsl, List<String> errors) {
        String incoming = dsl.entry();
        if (incoming == null) return;

        List<StrategyRecord> active = strategyRepository.findByIsActiveTrue(
                org.springframework.data.domain.Pageable.unpaged());

        for (StrategyRecord existing : active) {
            if (existing.getDsl() == null) continue;
            String existingEntry = existing.getDsl().entry();
            if (existingEntry == null) continue;

            int distance = levenshtein(incoming, existingEntry);
            if (distance < MIN_LEVENSHTEIN_DISTANCE) {
                errors.add("strategy entry expression is too similar to existing strategy '"
                        + existing.getName()
                        + "' (Levenshtein distance=" + distance
                        + ", minimum=" + MIN_LEVENSHTEIN_DISTANCE + ")");
                return; // one hit is enough to reject
            }
        }
    }

    // -------------------------------------------------------------------------
    // Levenshtein distance
    // -------------------------------------------------------------------------

    static int levenshtein(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                    Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[m][n];
    }

    // -------------------------------------------------------------------------

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}