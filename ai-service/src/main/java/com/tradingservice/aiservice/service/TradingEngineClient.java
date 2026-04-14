package com.tradingservice.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingservice.aiservice.domain.BacktestResultRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP client for trading-engine service (port 8081).
 */
@Component
public class TradingEngineClient {

    private static final Logger log = LoggerFactory.getLogger(TradingEngineClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String tradingEngineUrl;

    public TradingEngineClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${services.trading-engine-url:http://localhost:8081}") String tradingEngineUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.tradingEngineUrl = tradingEngineUrl;
    }

    /**
     * Run a backtest for the given strategy DSL.
     * Returns the backtest result or null if failed.
     */
    public BacktestResultRecord backtest(String dsl) {
        try {
            Map<String, Object> request = objectMapper.readValue(dsl, Map.class);

            // Add default backtest parameters
            Map<String, Object> backtestRequest = Map.of(
                    "strategy", request,
                    "symbol", "BTCUSDT",
                    "interval", "1h",
                    "startDate", Instant.now().minusSeconds(180L * 24 * 60 * 60).toString(), // 180 days
                    "endDate", Instant.now().toString(),
                    "executionModel", "PERCENT_OF_BALANCE",
                    "positionSizePct", 10.0
            );

            Map<String, Object> response = restClient.post()
                    .uri(tradingEngineUrl + "/api/v1/simulate")
                    .body(backtestRequest)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("Empty response from trading-engine");
                return null;
            }

            return mapToBacktestResult(response);

        } catch (Exception e) {
            log.error("Backtest failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Map trading-engine response to BacktestResultRecord.
     */
    private BacktestResultRecord mapToBacktestResult(Map<String, Object> response) {
        try {
            Object metricsObj = response.get("metrics");
            if (!(metricsObj instanceof Map)) {
                log.warn("Invalid metrics in response");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) metricsObj;

            Object detailObj = response.get("detail");
            @SuppressWarnings("unchecked")
            Map<String, Object> detail = detailObj instanceof Map ? (Map<String, Object>) detailObj : Map.of();

            return BacktestResultRecord.builder()
                    .id(java.util.UUID.randomUUID())
                    .strategyId(null) // Not yet associated with a strategy
                    .symbol((String) response.get("symbol"))
                    .interval((String) response.get("interval"))
                    .fromTime(Instant.parse((String) response.get("startDate")))
                    .toTime(Instant.parse((String) response.get("endDate")))
                    .totalTrades(getInteger(metrics, "totalTrades"))
                    .winRate(getDouble(metrics, "winRate"))
                    .totalPnl(getDouble(metrics, "totalPnl"))
                    .sharpeRatio(getDouble(metrics, "sharpeRatio"))
                    .maxDrawdown(getDouble(metrics, "maxDrawdown"))
                    .isStatisticallyValid(getBoolean(metrics, "isStatisticallyValid"))
                    .validationNote((String) metrics.get("validationNote"))
                    .resultDetail(detail)
                    .build();

        } catch (Exception e) {
            log.error("Failed to map backtest result: {}", e.getMessage());
            return null;
        }
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return null;
    }
}
