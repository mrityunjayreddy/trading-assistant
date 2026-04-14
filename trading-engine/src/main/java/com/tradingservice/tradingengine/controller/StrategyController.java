package com.tradingservice.tradingengine.controller;

import com.tradingservice.tradingengine.dto.StrategyDescriptor;
import com.tradingservice.tradingengine.strategy.StrategyFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/strategies", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyFactory strategyFactory;

    @GetMapping
    public List<StrategyDescriptor> listStrategies() {
        return strategyFactory.getAvailableStrategies();
    }
}
