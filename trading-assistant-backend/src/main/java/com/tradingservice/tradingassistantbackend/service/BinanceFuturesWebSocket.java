package com.tradingservice.tradingassistantbackend.service;


import com.tradingservice.tradingassistantbackend.model.Candle;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BinanceFuturesWebSocket extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceFuturesWebSocket.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final TradingDataService tradingDataService;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean shuttingDown = false;

    public BinanceFuturesWebSocket(String symbol, TradingDataService tradingDataService) throws Exception {
        super(new URI("wss://fstream.binance.com/ws/" + symbol.toLowerCase() + "@kline_1m"));
        this.tradingDataService = tradingDataService;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("Connected to Binance Futures WebSocket: {}", getURI());
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode json = MAPPER.readTree(message);
            JsonNode kline = json.get("k");
            if (kline == null) {
                return;
            }

            Candle candle = new Candle(
                    kline.get("s").asText(),
                    kline.get("t").asLong(),
                    kline.get("T").asLong(),
                    kline.get("o").asDouble(),
                    kline.get("h").asDouble(),
                    kline.get("l").asDouble(),
                    kline.get("c").asDouble(),
                    kline.get("v").asDouble(),
                    kline.get("x").asBoolean(),
                    kline.get("i").asText("1m")
            );

            tradingDataService.publishCandle(candle);
        } catch (Exception exception) {
            log.error("Failed to process Binance message: {}", exception.getMessage(), exception);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("Binance WebSocket closed: uri={} code={} reason='{}' remote={}", getURI(), code, reason, remote);
        if (!shuttingDown) {
            log.info("Scheduling reconnect in {}s for {}", RECONNECT_DELAY_SECONDS, getURI());
            reconnectExecutor.schedule(() -> {
                try {
                    reconnect();
                } catch (Exception e) {
                    log.error("Reconnect failed for {}: {}", getURI(), e.getMessage(), e);
                }
            }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("Binance WebSocket error: uri={} error={}", getURI(), ex.getMessage(), ex);
    }

    public void shutdown() {
        shuttingDown = true;
        reconnectExecutor.shutdownNow();
    }
}