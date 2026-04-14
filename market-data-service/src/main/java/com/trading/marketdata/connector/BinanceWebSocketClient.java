package com.trading.marketdata.connector;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import com.trading.marketdata.config.MarketDataProperties;

@Component
public class BinanceWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private final ReactorNettyWebSocketClient webSocketClient;
    private final MarketDataProperties properties;

    public BinanceWebSocketClient(
        ReactorNettyWebSocketClient webSocketClient,
        MarketDataProperties properties
    ) {
        this.webSocketClient = webSocketClient;
        this.properties = properties;
    }

    public Flux<String> tradeStream(String symbol) {
        String normalizedSymbol = symbol.toLowerCase(Locale.ROOT);
        String streamName = normalizedSymbol + "@trade";
        URI endpoint = URI.create(properties.getWebsocket().getBaseUrl() + "/" + streamName);

        return Flux.defer(() -> connect(endpoint, streamName))
            .repeat()
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, properties.getWebsocket().getReconnectDelay())
                    .maxBackoff(properties.getWebsocket().getMaxReconnectDelay())
                    .doBeforeRetry(signal -> log.warn(
                        "Reconnecting Binance WebSocket for {} after failure: {}",
                        streamName,
                        signal.failure().getMessage()
                    ))
            );
    }

    private Flux<String> connect(URI endpoint, String streamName) {
        return Flux.create(sink -> {
            log.info("Connecting to Binance WebSocket...");
            webSocketClient.execute(
                endpoint,
                session -> {
                    log.info("Subscribed to {}", streamName);
                    return session.receive()
                        .map(message -> message.getPayloadAsText())
                        .doOnNext(payload -> emitPayload(streamName, payload, sink))
                        .doOnError(error -> log.error("WebSocket error for {}: {}", streamName, error.getMessage(), error))
                        .doFinally(signal -> log.warn("Binance WebSocket closed for {} with signal {}", streamName, signal))
                        .then();
                }
            ).subscribe(
                unused -> { },
                sink::error,
                sink::complete
            );
        });
    }

    /**
     * Opens a Binance combined-stream kline feed for {@code symbol} at {@code interval}.
     * Connects to: wss://fstream.binance.com/stream?streams={symbol}@kline_{interval}
     * Uses the same exponential-backoff reconnect logic as {@link #tradeStream}.
     *
     * @param klineStreamBaseUrl  base URL, e.g. {@code wss://fstream.binance.com/stream}
     * @param symbol              lowercase symbol, e.g. {@code btcusdt}
     * @param interval            kline interval, e.g. {@code 1m}
     */
    public Flux<String> klineStream(String klineStreamBaseUrl, String symbol, String interval) {
        String normalizedSymbol = symbol.toLowerCase(Locale.ROOT);
        String streamName = normalizedSymbol + "@kline_" + interval;
        URI endpoint = URI.create(klineStreamBaseUrl + "?streams=" + streamName);

        return Flux.defer(() -> connect(endpoint, streamName))
            .repeat()
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, properties.getWebsocket().getReconnectDelay())
                    .maxBackoff(properties.getWebsocket().getMaxReconnectDelay())
                    .doBeforeRetry(signal -> log.warn(
                        "Reconnecting Binance kline WebSocket for {} after failure: {}",
                        streamName,
                        signal.failure().getMessage()
                    ))
            );
    }

    private void emitPayload(String streamName, String payload, FluxSink<String> sink) {
        log.debug("Received raw payload for {}: {}", streamName, payload);
        sink.next(payload);
    }
}
