package com.trading.marketdata.service;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.connector.BinanceWebSocketClient;
import com.trading.marketdata.model.TradeEvent;
import com.trading.marketdata.normalizer.BinanceTradeNormalizer;
import com.trading.marketdata.publisher.KafkaMarketEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Service
public class MarketDataStreamService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataStreamService.class);

    private final BinanceWebSocketClient binanceWebSocketClient;
    private final BinanceTradeNormalizer binanceTradeNormalizer;
    private final KafkaMarketEventPublisher kafkaMarketEventPublisher;
    private final MarketDataProperties properties;
    private Disposable subscription;

    public MarketDataStreamService(
        BinanceWebSocketClient binanceWebSocketClient,
        BinanceTradeNormalizer binanceTradeNormalizer,
        KafkaMarketEventPublisher kafkaMarketEventPublisher,
        MarketDataProperties properties
    ) {
        this.binanceWebSocketClient = binanceWebSocketClient;
        this.binanceTradeNormalizer = binanceTradeNormalizer;
        this.kafkaMarketEventPublisher = kafkaMarketEventPublisher;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        if (!properties.getStream().isAutoStart()) {
            log.info("Market data streaming auto-start is disabled");
            return;
        }

        subscription = Flux.fromIterable(properties.getSymbols())
            .map(symbol -> symbol.toLowerCase(Locale.ROOT))
            .flatMap(this::streamTradesForSymbol)
            .subscribe(
                unused -> { },
                error -> log.error("Market data stream terminated unexpectedly", error)
            );
    }

    private Flux<Void> streamTradesForSymbol(String symbol) {
        return binanceWebSocketClient.tradeStream(symbol)
            .map(binanceTradeNormalizer::normalize)
            .doOnNext(this::logTradeEvent)
            .flatMap(kafkaMarketEventPublisher::publishTradeEvent);
    }

    private void logTradeEvent(TradeEvent tradeEvent) {
        log.info(
            "Received trade event {} price={} qty={}",
            tradeEvent.symbol(),
            tradeEvent.price(),
            tradeEvent.quantity()
        );
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
