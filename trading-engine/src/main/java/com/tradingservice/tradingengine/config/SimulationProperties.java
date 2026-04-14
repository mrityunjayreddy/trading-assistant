package com.tradingservice.tradingengine.config;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "trading.simulation")
public class SimulationProperties {

    @DecimalMin("0.0")
    private double initialBalance = 1000.0;

    @DecimalMin("0.0")
    private double feeRate = 0.0004;
}
