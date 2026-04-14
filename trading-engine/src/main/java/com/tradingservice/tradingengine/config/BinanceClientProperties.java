package com.tradingservice.tradingengine.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "binance")
public class BinanceClientProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String klinesPath;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(15);

    @Min(1)
    @Max(1500)
    private int defaultLimit = 1000;
}
