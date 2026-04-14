package com.tradingservice.tradingassistantbackend.service;

import com.tradingservice.tradingassistantbackend.model.Candle;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class TradingDataService {

    // Injected for closed-candle side-effects (Kafka + DB). Nullable to allow
    // the service to start even if Kafka/Postgres are not yet available in dev.
    private final ClosedCandleKafkaPublisher kafkaPublisher;
    private final ClosedCandleDbPersister dbPersister;

    private static final int MAX_CANDLES_PER_SYMBOL = 500;
    private static final long SSE_TIMEOUT_MILLIS = 0L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final Map<String, Deque<Candle>> candleStore = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<EmitterSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    public TradingDataService(
            ClosedCandleKafkaPublisher kafkaPublisher,
            ClosedCandleDbPersister dbPersister
    ) {
        this.kafkaPublisher = kafkaPublisher;
        this.dbPersister = dbPersister;
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats, HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void publishCandle(Candle candle) {
        String normalizedSymbol = normalizeSymbol(candle.symbol());
        Candle normalizedCandle = new Candle(
                normalizedSymbol,
                candle.openTime(),
                candle.closeTime(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.closed(),
                candle.interval()
        );

        Deque<Candle> candles = candleStore.computeIfAbsent(normalizedSymbol, ignored -> new ConcurrentLinkedDeque<>());

        candles.removeIf(existing -> existing.openTime() == normalizedCandle.openTime());
        candles.addLast(normalizedCandle);

        while (candles.size() > MAX_CANDLES_PER_SYMBOL) {
            candles.pollFirst();
        }

        if (normalizedCandle.closed()) {
            try { kafkaPublisher.publish(normalizedCandle); }
            catch (Exception e) { log.error("Kafka publish failed for {}", normalizedCandle.symbol(), e); }

            try { dbPersister.persist(normalizedCandle); }
            catch (Exception e) { log.error("DB persist failed for {}", normalizedCandle.symbol(), e); }
        }


        broadcast(normalizedCandle);
    }

    public List<Candle> getRecentCandles(String symbol, int limit) {
        String normalizedSymbol = normalizeSymbol(symbol);
        Deque<Candle> candles = candleStore.getOrDefault(normalizedSymbol, new ConcurrentLinkedDeque<>());

        return candles.stream()
                .sorted(Comparator.comparingLong(Candle::openTime))
                .skip(Math.max(0, candles.size() - limit))
                .toList();
    }

    public SseEmitter subscribe(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        EmitterSubscription subscription = new EmitterSubscription(normalizedSymbol, emitter);
        subscriptions.add(subscription);

        emitter.onCompletion(() -> removeSubscription(subscription));
        emitter.onTimeout(() -> removeSubscription(subscription));
        emitter.onError(error -> removeSubscription(subscription));

        if (!sendEvent(subscription, SseEmitter.event()
                .name("status")
                .data(Map.of("symbol", normalizedSymbol, "connected", true)))) {
            removeSubscription(subscription);
        }

        return emitter;
    }

    private void broadcast(Candle candle) {
        List<EmitterSubscription> staleSubscriptions = new ArrayList<>();
        for (EmitterSubscription subscription : subscriptions) {
            if (!Objects.equals(subscription.symbol(), candle.symbol())) {
                continue;
            }

            if (!sendEvent(subscription, SseEmitter.event()
                    .name("candle")
                    .data(candle))) {
                staleSubscriptions.add(subscription);
            }
        }
        staleSubscriptions.forEach(this::removeSubscription);
    }

    private void sendHeartbeats() {
        List<EmitterSubscription> staleSubscriptions = new ArrayList<>();

        for (EmitterSubscription subscription : subscriptions) {
            if (!sendEvent(subscription, SseEmitter.event()
                    .name("heartbeat")
                    .data(Map.of("connected", true)))) {
                staleSubscriptions.add(subscription);
            }
        }

        staleSubscriptions.forEach(this::removeSubscription);
    }

    private boolean sendEvent(EmitterSubscription subscription, SseEmitter.SseEventBuilder event) {
        try {
            subscription.emitter().send(event);
            return true;
        } catch (IOException | IllegalStateException exception) {
            return false;
        }
    }

    private void removeSubscription(EmitterSubscription subscription) {
        subscriptions.remove(subscription);
        try {
            subscription.emitter().complete();
        } catch (IllegalStateException ignored) {
            // The client is already gone; nothing else to do.
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "BTCUSDT" : symbol.trim().toUpperCase();
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
        subscriptions.forEach(this::removeSubscription);
    }

    private record EmitterSubscription(String symbol, SseEmitter emitter) {
    }
}
