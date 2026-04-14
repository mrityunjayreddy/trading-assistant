package com.tradingservice.tradingassistantbackend.service;

import com.tradingservice.tradingassistantbackend.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes a closed {@link Candle} to Kafka topic {@code market.kline.{interval}}.
 * Called from {@link TradingDataService#publishCandle} when a candle with
 * {@code closed=true} arrives — do NOT call for open/in-progress candles.
 *
 * Topic:  market.kline.{candle.interval()}   e.g. market.kline.1m
 * Key:    BINANCE:{candle.symbol()}           e.g. BINANCE:BTCUSDT
 */
@Component
public class ClosedCandleKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClosedCandleKafkaPublisher.class);
    private static final String TOPIC_PREFIX = "market.kline.";

    private final KafkaTemplate<String, String> candleKafkaTemplate;
    private final ObjectMapper objectMapper;

    public ClosedCandleKafkaPublisher(
        KafkaTemplate<String, String> candleKafkaTemplate,
        ObjectMapper objectMapper
    ) {
        this.candleKafkaTemplate = candleKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(Candle candle) {
        String topic = TOPIC_PREFIX + candle.interval();
        String key = "BINANCE:" + candle.symbol();
        try {
            String json = objectMapper.writeValueAsString(candle);
            candleKafkaTemplate.send(topic, key, json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish closed candle to {}: symbol={} error={}",
                            topic, candle.symbol(), ex.getMessage());
                    } else {
                        log.info("Published closed candle to {}: symbol={}", topic, candle.symbol());
                    }
                });
        } catch (Exception e) {
            log.error("Failed to serialize Candle for Kafka: symbol={} interval={}",
                candle.symbol(), candle.interval(), e);
        }
    }
}