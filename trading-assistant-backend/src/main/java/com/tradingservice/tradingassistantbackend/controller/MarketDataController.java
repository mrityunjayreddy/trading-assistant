package com.tradingservice.tradingassistantbackend.controller;

import com.tradingservice.tradingassistantbackend.model.Candle;
import com.tradingservice.tradingassistantbackend.service.BinanceMarketStreamManager;
import com.tradingservice.tradingassistantbackend.service.TradingDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
public class MarketDataController {

    private final TradingDataService tradingDataService;
    private final BinanceMarketStreamManager streamManager;

    public MarketDataController(TradingDataService tradingDataService, BinanceMarketStreamManager streamManager) {
        this.tradingDataService = tradingDataService;
        this.streamManager = streamManager;
    }

    @GetMapping("/api/markets")
    public Map<String, List<String>> getMarkets() {
        return Map.of("symbols", streamManager.getSymbols());
    }

    @GetMapping("/api/candles")
    public List<Candle> getCandles(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "120") int limit
    ) {
        int normalizedLimit = Math.max(10, Math.min(limit, 500));
        return tradingDataService.getRecentCandles(symbol, normalizedLimit);
    }

    @GetMapping("/api/market-stream")
    public SseEmitter streamCandles(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return tradingDataService.subscribe(symbol);
    }
}
