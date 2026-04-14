package com.tradingservice.tradingengine.dto;

import com.tradingservice.tradingengine.model.ExecutionModelType;
import com.tradingservice.tradingengine.model.TradeDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionModelRequest {

    @NotNull
    @Builder.Default
    private ExecutionModelType type = ExecutionModelType.FULL_BALANCE;

    @Valid
    @NotNull
    @Builder.Default
    private ExecutionParameters params = ExecutionParameters.builder().build();

    @NotNull
    @Builder.Default
    private TradeDirection tradeDirection = TradeDirection.LONG_ONLY;
}
