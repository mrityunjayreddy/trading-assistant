package com.tradingservice.strategyservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StrategyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyServiceApplication.class, args);
    }
}