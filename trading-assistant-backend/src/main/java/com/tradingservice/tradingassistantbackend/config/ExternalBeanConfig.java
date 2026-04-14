package com.tradingservice.tradingassistantbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Component
public class ExternalBeanConfig {

    @Bean
    public ObjectMapper objectMapper() { return new ObjectMapper(); }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

}
