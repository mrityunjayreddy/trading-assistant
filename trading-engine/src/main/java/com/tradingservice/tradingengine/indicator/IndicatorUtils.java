package com.tradingservice.tradingengine.indicator;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.Locale;
import java.util.Map;

public final class IndicatorUtils {

    private IndicatorUtils() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static int getInt(IndicatorDefinition definition, String key, int fallback, String... aliases) {
        Object raw = getRaw(definition, key, aliases);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException exception) {
            throw invalidParam(definition, key, "must be an integer");
        }
    }

    public static int requireInt(IndicatorDefinition definition, String key, String... aliases) {
        Object raw = getRaw(definition, key, aliases);
        if (raw == null) {
            throw invalidParam(definition, key, "is required");
        }
        return getInt(definition, key, 0, aliases);
    }

    public static double getDouble(IndicatorDefinition definition, String key, double fallback, String... aliases) {
        Object raw = getRaw(definition, key, aliases);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException exception) {
            throw invalidParam(definition, key, "must be numeric");
        }
    }

    public static String getString(IndicatorDefinition definition, String key, String fallback, String... aliases) {
        Object raw = getRaw(definition, key, aliases);
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    public static String requireString(IndicatorDefinition definition, String key, String... aliases) {
        String value = getString(definition, key, null, aliases);
        if (!hasText(value)) {
            throw invalidParam(definition, key, "is required");
        }
        return value;
    }

    public static Object getObject(IndicatorDefinition definition, String key, String... aliases) {
        return getRaw(definition, key, aliases);
    }

    public static String effectiveType(IndicatorDefinition definition) {
        String primary = normalizeType(definition.getType());
        String subType = normalizeType(definition.getSubType());

        if (isCategory(primary) && hasText(subType)) {
            return subType;
        }
        return primary;
    }

    public static String cacheKey(IndicatorDefinition definition) {
        if (hasText(definition.getId())) {
            return normalize(definition.getId());
        }
        return "__inline__:" + effectiveType(definition) + ":" + Integer.toHexString(definition.hashCode());
    }

    public static InvalidStrategyConfigurationException invalidParam(
            IndicatorDefinition definition,
            String key,
            String message
    ) {
        return new InvalidStrategyConfigurationException("Indicator '" + definition.getId() + "' param '" + key + "' " + message);
    }

    private static Object getRaw(IndicatorDefinition definition, String key, String... aliases) {
        Map<String, Object> params = definition.getParams();
        if (params == null || params.isEmpty()) {
            return null;
        }

        if (params.containsKey(key)) {
            return params.get(key);
        }

        for (String alias : aliases) {
            if (params.containsKey(alias)) {
                return params.get(alias);
            }
        }

        return null;
    }

    private static boolean isCategory(String value) {
        return switch (value) {
            case "TREND", "MOMENTUM", "VOLATILITY", "VOLUME", "UTILITY", "MATHEMATICAL", "HYBRID",
                    "TREND_MOMENTUM_HYBRID" -> true;
            default -> false;
        };
    }
}
