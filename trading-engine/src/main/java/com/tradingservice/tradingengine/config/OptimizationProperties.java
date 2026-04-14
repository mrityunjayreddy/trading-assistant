package com.tradingservice.tradingengine.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "trading.optimization")
public class OptimizationProperties {

    @Min(1)
    @Max(32)
    private int threadPoolSize = 6;

    @Min(1)
    @Max(50)
    private int topResultsLimit = 5;

    private Duration taskTimeout = Duration.ofSeconds(10);
}
