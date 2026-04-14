package com.tradingservice.strategyservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures a {@link RestClient} pointing at the trading-engine.
 *
 * <p>Connect timeout: 5 s — fail fast if the engine is not up.<br>
 * Read timeout: 10 s — a simulation can take a few seconds for large datasets.</p>
 */
@Configuration
public class RestClientConfig {

    @Value("${trading-engine.base-url}")
    private String tradingEngineBaseUrl;

    @Bean
    public RestClient tradingEngineClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return builder
                .baseUrl(tradingEngineBaseUrl)
                .requestFactory(factory)
                .build();
    }
}