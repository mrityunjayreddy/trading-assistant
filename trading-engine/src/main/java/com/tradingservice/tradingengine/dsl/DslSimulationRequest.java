package com.tradingservice.tradingengine.dsl;

import com.tradingservice.tradingengine.dto.ExecutionModelRequest;
import com.tradingservice.tradingengine.dto.SimulationAssumptions;
import com.tradingservice.tradingengine.dto.SimulationRange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/simulate/dsl} and
 * {@code POST /api/v1/backtest/validate}.
 *
 * Mirrors {@link com.tradingservice.tradingengine.dto.SimulationRequest} but replaces
 * the {@code strategy} name + params with a full {@link StrategyDSL}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DslSimulationRequest {

    @NotBlank
    private String symbol;

    @NotBlank
    private String interval;

    @Valid
    @NotNull
    private StrategyDSL dsl;

    @Valid
    @Builder.Default
    private ExecutionModelRequest execution = ExecutionModelRequest.builder().build();

    @Valid
    @Builder.Default
    private SimulationAssumptions assumptions = SimulationAssumptions.builder().build();

    @Valid
    @Builder.Default
    private SimulationRange range = SimulationRange.builder().build();

    /**
     * Returns a copy of this request with the date range restricted to the
     * last 20% of the original range — used for held-out validation.
     *
     * <p>If no range is set, defaults to the most recent 73 calendar days
     * (approximately 20% of one trading year).</p>
     */
    public DslSimulationRequest withValidationRange() {
        long nowMs = System.currentTimeMillis();
        long oneYearMs = 365L * 24 * 60 * 60 * 1000;

        long endMs   = (range != null && range.getEndTime()   != null) ? range.getEndTime()   : nowMs;
        long startMs = (range != null && range.getStartTime() != null) ? range.getStartTime() : nowMs - oneYearMs;

        long span = endMs - startMs;
        if (span <= 0) {
            span = oneYearMs;
            startMs = endMs - span;
        }

        // Validation window = last 20% of the total span
        long validationStart = startMs + (long) (span * 0.80);

        SimulationRange validationRange = SimulationRange.builder()
                .startTime(validationStart)
                .endTime(endMs)
                .build();

        return DslSimulationRequest.builder()
                .symbol(this.symbol)
                .interval(this.interval)
                .dsl(this.dsl)
                .execution(this.execution)
                .assumptions(this.assumptions)
                .range(validationRange)
                .build();
    }
}