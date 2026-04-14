package com.trading.marketdata.persistence;


import java.sql.Timestamp;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Batch Kafka consumer that persists closed klines from ALL market.kline.* topics
 * into the {@code market_data} PostgreSQL table.
 *
 * Batching is driven by Kafka fetch settings configured in KlineKafkaConfig:
 * max-poll-records=50 and fetch.max.wait.ms=10000 → "50 records OR 10 seconds".
 *
 * On batch failure: logs full context, retries once, then discards the batch.
 * Individual parse failures within a batch are skipped without aborting the rest.
 */
@Component
public class KlinePersistenceConsumer {

    private static final Logger log = LoggerFactory.getLogger(KlinePersistenceConsumer.class);

    private static final String UPSERT_SQL =
        "INSERT INTO market_data " +
        "(exchange, symbol, interval, open_time, close_time, open, high, low, close, volume, trade_count, is_closed) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true) " +
        "ON CONFLICT (exchange, symbol, interval, open_time) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KlinePersistenceConsumer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topicPattern = "market\\.kline\\..*",
        groupId = "kline-persister",
        containerFactory = "klineBatchContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, String>> records) {
        if (records.isEmpty()) {
            return;
        }

        log.debug("Received batch of {} kline records", records.size());

        List<Object[]> batchArgs = records.stream()
            .map(record -> parseToRow(record.value()))
            .filter(row -> row != null)
            .toList();

        if (batchArgs.isEmpty()) {
            log.warn("All {} records in batch failed to parse — skipping batch", records.size());
            return;
        }

        try {
            flushBatch(batchArgs);
        } catch (Exception firstFailure) {
            log.error(
                "Batch insert failed (attempt 1/2): batchSize={} topics={} error={}",
                batchArgs.size(),
                records.stream().map(ConsumerRecord::topic).distinct().toList(),
                firstFailure.getMessage(),
                firstFailure
            );
            try {
                flushBatch(batchArgs);
                log.info("Batch insert succeeded on retry: size={}", batchArgs.size());
            } catch (Exception secondFailure) {
                log.error(
                    "Batch insert failed permanently (attempt 2/2): batchSize={} — discarding batch. Error: {}",
                    batchArgs.size(),
                    secondFailure.getMessage(),
                    secondFailure
                );
            }
        }
    }

    private void flushBatch(List<Object[]> batchArgs) {
        jdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
        log.debug("Flushed {} kline rows to market_data", batchArgs.size());
    }

    /**
     * Parses a JSON kline message into a JDBC batch-update row array.
     * Handles both KlineEvent format (from market-data-service) and Candle format
     * (from trading-assistant-backend) by checking for alternative field names.
     *
     * Returns null if the message cannot be parsed — the caller skips null rows.
     */
    private Object[] parseToRow(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            String exchange   = textOrDefault(node, "exchange", "BINANCE");
            String symbol     = node.path("symbol").asText();
            String interval   = node.path("interval").asText();

            // openTime: "openTime" (both formats use epoch millis)
            long openEpochMs  = node.path("openTime").asLong();
            long closeEpochMs = node.path("closeTime").asLong();

            double open   = node.path("open").asDouble();
            double high   = node.path("high").asDouble();
            double low    = node.path("low").asDouble();
            double close  = node.path("close").asDouble();
            double volume = node.path("volume").asDouble();
            long tradeCount = node.path("tradeCount").asLong(0L);

            if (symbol.isEmpty() || interval.isEmpty() || openEpochMs == 0) {
                log.warn("Skipping malformed kline record: {}", json);
                return null;
            }

            return new Object[]{
                exchange,
                symbol,
                interval,
                new Timestamp(openEpochMs),
                new Timestamp(closeEpochMs),
                open, high, low, close,
                volume,
                tradeCount
            };
        } catch (Exception e) {
            log.warn("Failed to parse kline JSON, skipping record: {} — error: {}", json, e.getMessage());
            return null;
        }
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.asText().isEmpty() ? defaultValue : n.asText();
    }
}