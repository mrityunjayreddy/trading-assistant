package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.dto.StrategyDescriptor;
import java.util.List;
import java.util.Map;

public interface StrategyFactory {

    Strategy create(String strategyName, Map<String, Object> parameters);

    List<StrategyDescriptor> getAvailableStrategies();
}
