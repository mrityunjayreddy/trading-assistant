package com.tradingservice.aiservice.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to generate a new trading strategy using AI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRequest {

    @NotBlank(message = "symbol is required")
    private String symbol;

    @NotBlank(message = "interval is required")
    private String interval;

    @NotBlank(message = "objective is required")
    private String objective;
}
