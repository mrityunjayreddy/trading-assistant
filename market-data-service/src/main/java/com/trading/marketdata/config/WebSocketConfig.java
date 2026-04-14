package com.trading.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

@Configuration
public class WebSocketConfig {

    @Bean
    public ReactorNettyWebSocketClient reactorNettyWebSocketClient() {
        return new ReactorNettyWebSocketClient();
    }
}
