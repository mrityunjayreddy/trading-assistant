package com.tradingservice.tradingassistantbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendOrigin;

    public WebConfig(@Value("${app.frontend.origin:http://localhost:5173}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Existing market-data endpoints — GET only
        registry.addMapping("/api/markets")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET")
                .allowedHeaders("*");

        registry.addMapping("/api/candles")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET")
                .allowedHeaders("*");

        registry.addMapping("/api/market-stream")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET")
                .allowedHeaders("*");

        // Proxy routes to downstream services — all methods required
        registry.addMapping("/api/strategies/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("*");

        registry.addMapping("/api/sim/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("*");

        registry.addMapping("/api/ai/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("*");
    }
}
