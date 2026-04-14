package com.tradingservice.strategyservice.dto;

import java.util.UUID;

public record RegisterStrategyResponse(
        UUID id,
        String name,
        String source,
        String status
) {}