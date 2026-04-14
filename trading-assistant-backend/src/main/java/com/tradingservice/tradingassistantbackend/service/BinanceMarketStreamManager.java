package com.tradingservice.tradingassistantbackend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BinanceMarketStreamManager {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketStreamManager.class);

    private final TradingDataService tradingDataService;
    private final List<String> symbols;
    private final List<BinanceFuturesWebSocket> clients = new ArrayList<>();

    public BinanceMarketStreamManager(
            TradingDataService tradingDataService,
            @Value("${trading.symbols:btcusdt,ethusdt,bnbusdt}") List<String> symbols
    ) {
        this.tradingDataService = tradingDataService;
        this.symbols = symbols;
    }

    @PostConstruct
    public void start() {
        for (String symbol : symbols) {
            try {
                BinanceFuturesWebSocket client = new BinanceFuturesWebSocket(symbol.trim(), tradingDataService);
                client.connect();
                clients.add(client);
            } catch (Exception exception) {
                log.error("Failed to connect Binance stream for symbol {}: {}", symbol, exception.getMessage(), exception);
            }
        }
    }

    @PreDestroy
    public void stop() {
        for (BinanceFuturesWebSocket client : clients) {
            try {
                client.shutdown();
                client.closeBlocking();
            } catch (Exception exception) {
                log.error("Failed to close Binance stream {}: {}", client.getURI(), exception.getMessage(), exception);
            }
        }
    }

    public List<String> getSymbols() {
        return symbols.stream()
                .map(symbol -> symbol.trim().toUpperCase())
                .toList();
    }
}