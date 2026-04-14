package com.tradingservice.tradingengine.service;

import com.tradingservice.tradingengine.dto.ExecutionModelDescriptor;
import com.tradingservice.tradingengine.dto.StrategyParameterDescriptor;
import com.tradingservice.tradingengine.model.ExecutionModelType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExecutionModelCatalogService {

    public List<ExecutionModelDescriptor> getExecutionModels() {
        return List.of(
                ExecutionModelDescriptor.builder()
                        .value(ExecutionModelType.FULL_BALANCE)
                        .label("Full Balance")
                        .description("Deploys the full available cash balance on each entry signal for long or short trades.")
                        .parameters(List.of())
                        .build(),
                ExecutionModelDescriptor.builder()
                        .value(ExecutionModelType.PERCENT_OF_BALANCE)
                        .label("Percent Of Balance")
                        .description("Deploys a configurable percentage of current cash balance on each entry signal for long or short trades.")
                        .parameters(List.of(
                                StrategyParameterDescriptor.builder()
                                        .name("allocationPercent")
                                        .label("Allocation Percent")
                                        .type("number")
                                        .defaultValue(25)
                                        .minValue(1)
                                        .maxValue(100)
                                        .required(true)
                                        .build()
                        ))
                        .build(),
                ExecutionModelDescriptor.builder()
                        .value(ExecutionModelType.FIXED_AMOUNT)
                        .label("Fixed Amount")
                        .description("Deploys a fixed USDT amount per entry signal, capped by available cash.")
                        .parameters(List.of(
                                StrategyParameterDescriptor.builder()
                                        .name("fixedAmount")
                                        .label("Fixed Amount (USDT)")
                                        .type("number")
                                        .defaultValue(250)
                                        .minValue(1)
                                        .maxValue(1000000)
                                        .required(true)
                                        .build()
                        ))
                        .build()
        );
    }
}
