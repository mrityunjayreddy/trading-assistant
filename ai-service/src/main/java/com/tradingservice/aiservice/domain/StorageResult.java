package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result of attempting to store a backtest result in strategy_memory.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageResult {

    private boolean stored;
    private String rejectedReason;
    private UUID memoryId;

    public static StorageResult rejected(String reason) {
        return StorageResult.builder()
                .stored(false)
                .rejectedReason(reason)
                .build();
    }

    public static StorageResult stored(UUID memoryId) {
        return StorageResult.builder()
                .stored(true)
                .memoryId(memoryId)
                .build();
    }
}
