package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Health metrics for the learning loop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoopHealth {

    private Double memoryQuality;       // Avg sharpe of last 20 stored memories
    private Double generationSuccessRate; // Promotions / generations * 100
    private Integer memoryCount;        // Total rows in strategy_memory
    private Boolean isPaused;           // True if paused due to poor quality
    private Integer consecutivePoorRuns; // Count of consecutive runs with quality < 0.2

    public LoopHealth defaultValues() {
        return LoopHealth.builder()
                .memoryQuality(0.0)
                .generationSuccessRate(0.0)
                .memoryCount(0)
                .isPaused(false)
                .consecutivePoorRuns(0)
                .build();
    }
}
