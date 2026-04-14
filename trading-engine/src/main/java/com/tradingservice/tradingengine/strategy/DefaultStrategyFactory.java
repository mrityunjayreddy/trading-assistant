package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.dto.StrategyDescriptor;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultStrategyFactory implements StrategyFactory {

    private final StrategyRegistry strategyRegistry;

    @Override
    public Strategy create(String strategyName, Map<String, Object> parameters) {
        return strategyRegistry.create(strategyName, parameters);
    }

    @Override
    public List<StrategyDescriptor> getAvailableStrategies() {
        return strategyRegistry.getDescriptors();
    }
}
