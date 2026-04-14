package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for memory records returned to clients (includes computed similarity score).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecord {

    private UUID id;
    private String strategyName;
    private UUID strategyId;
    private String document;
    private Double sharpeRatio;
    private Double winRate;
    private Double maxDrawdown;
    private Integer tradeCount;
    private String verdict;
    private Map<String, Object> marketContext;
    private Instant createdAt;
    private Double similarityScore;      // Computed during retrieval
    private Double compositeScore;       // Re-ranked score
}
