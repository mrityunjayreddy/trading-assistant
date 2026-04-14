package com.trading.marketdata.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "marketdata")
public class MarketDataProperties {

    @NotBlank
    private String exchange = "binance";

    @NotEmpty
    private List<String> symbols = List.of("btcusdt");

    @NotNull
    private WebSocket websocket = new WebSocket();

    @NotNull
    private Kafka kafka = new Kafka();

    @NotNull
    private Stream stream = new Stream();

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public WebSocket getWebsocket() {
        return websocket;
    }

    public void setWebsocket(WebSocket websocket) {
        this.websocket = websocket;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

    public static class WebSocket {
        @NotBlank
        private String baseUrl = "wss://fstream.binance.com/ws";

        @NotNull
        private Duration reconnectDelay = Duration.ofSeconds(5);

        @NotNull
        private Duration maxReconnectDelay = Duration.ofSeconds(30);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getReconnectDelay() {
            return reconnectDelay;
        }

        public void setReconnectDelay(Duration reconnectDelay) {
            this.reconnectDelay = reconnectDelay;
        }

        public Duration getMaxReconnectDelay() {
            return maxReconnectDelay;
        }

        public void setMaxReconnectDelay(Duration maxReconnectDelay) {
            this.maxReconnectDelay = maxReconnectDelay;
        }
    }

    public static class Kafka {
        @NotBlank
        private String bootstrapServers = "localhost:9092";

        @NotBlank
        private String tradeTopic = "market.trades";

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getTradeTopic() {
            return tradeTopic;
        }

        public void setTradeTopic(String tradeTopic) {
            this.tradeTopic = tradeTopic;
        }
    }

    public static class Stream {
        private boolean autoStart = true;

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }
    }
}
