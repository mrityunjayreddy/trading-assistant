package com.tradingservice.tradingengine.optimization;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GridGenerator {

    public List<Map<String, Object>> generate(Map<String, List<Object>> paramGrid) {
        if (paramGrid == null || paramGrid.isEmpty()) {
            throw new InvalidStrategyConfigurationException("Parameter grid must contain at least one parameter");
        }

        List<String> keys = new ArrayList<>(paramGrid.keySet());
        List<Map<String, Object>> combinations = new ArrayList<>();
        expand(keys, paramGrid, 0, new LinkedHashMap<>(), combinations);
        return combinations;
    }

    private void expand(
            List<String> keys,
            Map<String, List<Object>> paramGrid,
            int index,
            Map<String, Object> current,
            List<Map<String, Object>> combinations
    ) {
        if (index == keys.size()) {
            combinations.add(Map.copyOf(current));
            return;
        }

        String key = keys.get(index);
        List<Object> values = paramGrid.get(key);
        if (values == null || values.isEmpty()) {
            throw new InvalidStrategyConfigurationException("Parameter grid entry '" + key + "' must include at least one value");
        }

        for (Object value : values) {
            current.put(key, value);
            expand(keys, paramGrid, index + 1, current, combinations);
        }
    }
}
