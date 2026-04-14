package com.trading.marketdata.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketdata.kline")
public class KlineProperties {

    private List<String> symbols = List.of("btcusdt", "ethusdt", "solusdt");
    private List<String> intervals = List.of("1m", "5m", "15m", "1h");
    private String streamBaseUrl = "wss://fstream.binance.com/stream";

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public List<String> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<String> intervals) {
        this.intervals = intervals;
    }

    public String getStreamBaseUrl() {
        return streamBaseUrl;
    }

    public void setStreamBaseUrl(String streamBaseUrl) {
        this.streamBaseUrl = streamBaseUrl;
    }
}