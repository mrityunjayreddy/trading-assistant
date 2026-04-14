package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of AI strategy generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResult {

    private String dsl;           // JSON string of the generated strategy
    private String reasoning;     // Explanation of how it was generated
    private String confidence;    // HIGH, MEDIUM, LOW
    private int retrievedMemoryCount;
    private String error;         // Non-null if generation failed

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    public static GenerationResult error(String message) {
        return GenerationResult.builder()
                .error(message)
                .build();
    }
}
