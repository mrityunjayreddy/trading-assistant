package com.tradingservice.tradingengine.strategy;

import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.Map;

public final class StrategyParameterReader {

    private final Map<String, Object> parameters;

    public StrategyParameterReader(Map<String, Object> parameters) {
        this.parameters = parameters == null ? Map.of() : parameters;
    }

    public int getInt(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw invalidType(key, "integer");
    }

    public double getDouble(String key, double defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw invalidType(key, "number");
    }

    public String getString(String key, String defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw invalidType(key, "string");
    }

    public Object getRaw(String key) {
        return parameters.get(key);
    }

    private InvalidStrategyConfigurationException invalidType(String key, String expectedType) {
        return new InvalidStrategyConfigurationException("Strategy parameter '" + key + "' must be a " + expectedType);
    }
}
