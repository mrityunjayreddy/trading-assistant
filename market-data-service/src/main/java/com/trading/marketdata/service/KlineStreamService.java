package com.trading.marketdata.service;


import com.trading.marketdata.config.KlineProperties;
import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.connector.BinanceWebSocketClient;
import com.trading.marketdata.model.KlineEvent;
import com.trading.marketdata.normalizer.BinanceKlineNormalizer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * Opens one Binance WebSocket per (symbol × interval) combination and
 * forwards CLOSED klines to Kafka topic {@code market.kline.{interval}}.
 *
 * Only closed klines (isClosed=true) are published — open/in-progress
 * candles are intentionally dropped to keep downstream consumers simple.
 */
@Service
public class KlineStreamService {

    private static final Logger log = LoggerFactory.getLogger(KlineStreamService.class);
    private static final String TOPIC_PREFIX = "market.kline.";

    private final BinanceWebSocketClient binanceWebSocketClient;
    private final BinanceKlineNormalizer klineNormalizer;
    private final KafkaTemplate<String, String> klineKafkaTemplate;
    private final KlineProperties klineProperties;
    private final MarketDataProperties marketDataProperties;
    private final ObjectMapper objectMapper;

    private Disposable subscription;

    public KlineStreamService(
        BinanceWebSocketClient binanceWebSocketClient,
        BinanceKlineNormalizer klineNormalizer,
        KafkaTemplate<String, String> klineKafkaTemplate,
        KlineProperties klineProperties,
        MarketDataProperties marketDataProperties,
        ObjectMapper objectMapper
    ) {
        this.binanceWebSocketClient = binanceWebSocketClient;
        this.klineNormalizer = klineNormalizer;
        this.klineKafkaTemplate = klineKafkaTemplate;
        this.klineProperties = klineProperties;
        this.marketDataProperties = marketDataProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        List<String> symbols = klineProperties.getSymbols();
        List<String> intervals = klineProperties.getIntervals();
        String streamBaseUrl = klineProperties.getStreamBaseUrl();

        log.info("Starting kline streams: symbols={} intervals={}", symbols, intervals);

        subscription = Flux.fromIterable(symbols)
            .map(s -> s.toLowerCase(Locale.ROOT))
            .flatMap(symbol ->
                Flux.fromIterable(intervals)
                    .flatMap(interval -> streamKlinesFor(streamBaseUrl, symbol, interval))
            )
            .subscribe(
                unused -> { },
                error -> log.error("Kline stream subscription terminated unexpectedly", error)
            );
    }

    private Flux<Void> streamKlinesFor(String streamBaseUrl, String symbol, String interval) {
        return binanceWebSocketClient.klineStream(streamBaseUrl, symbol, interval)
            .map(this::safeNormalize)
            .filter(event -> event != null && event.isClosed())
            .doOnNext(event -> log.debug(
                "Closed kline: symbol={} interval={} close={}",
                event.symbol(), event.interval(), event.close()
            ))
            .flatMap(event -> publishToKafka(event, interval));
    }

    private KlineEvent safeNormalize(String payload) {
        try {
            return klineNormalizer.normalize(payload);
        } catch (Exception e) {
            log.warn("Failed to normalize kline payload, skipping: {}", e.getMessage());
            return null;
        }
    }

    private Flux<Void> publishToKafka(KlineEvent event, String interval) {
        String topic = TOPIC_PREFIX + interval;
        String key = "BINANCE:" + event.symbol();
        try {
            String json = objectMapper.writeValueAsString(event);
            return Flux.from(
                reactor.core.publisher.Mono.fromFuture(() -> klineKafkaTemplate.send(topic, key, json))
                    .doOnSuccess(r -> log.debug("Published kline to {}: key={}", topic, key))
                    .doOnError(e -> log.error("Failed to publish kline to topic {} key={}", topic, key, e))
                    .then()
            );
        } catch (Exception e) {
            log.error("Failed to serialize KlineEvent for symbol={} interval={}", event.symbol(), interval, e);
            return Flux.empty();
        }
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}