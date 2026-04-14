package com.tradingservice.tradingengine.indicator;


import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class IndicatorFactory {

    private final IndicatorRegistry indicatorRegistry;
    private final ObjectMapper objectMapper;

    public Map<String, Indicator<Num>> buildIndicators(List<IndicatorDefinition> definitions, BarSeries series) {
        Map<String, Indicator<Num>> cache = new LinkedHashMap<>();
        registerBaseIndicators(series, cache);

        if (definitions == null) {
            return cache;
        }

        for (IndicatorDefinition definition : definitions) {
            createIndicator(definition, series, cache);
        }

        return cache;
    }

    public Indicator<Num> createIndicator(
            IndicatorDefinition definition,
            BarSeries series,
            Map<String, Indicator<Num>> cache
    ) {
        if (definition == null) {
            throw new InvalidStrategyConfigurationException("Indicator definition must be provided");
        }
        if (!IndicatorUtils.hasText(definition.getType())) {
            throw new InvalidStrategyConfigurationException("Indicator type must be provided for id=" + definition.getId());
        }

        String cacheKey = computeCacheKey(definition);
        Indicator<Num> cachedIndicator = cache.get(cacheKey);
        if (cachedIndicator != null) {
            return cachedIndicator;
        }

        IndicatorBuilder builder = indicatorRegistry.get(IndicatorUtils.effectiveType(definition));
        if (builder == null) {
            throw new InvalidStrategyConfigurationException("Unsupported indicator type: " + definition.getType()
                    + (IndicatorUtils.hasText(definition.getSubType()) ? " subType=" + definition.getSubType() : ""));
        }

        Indicator<Num> input = resolvePrimaryInput(definition, series, cache);
        Indicator<Num> built = builder.build(definition, input, series, this, cache);
        cache.put(cacheKey, built);

        if (IndicatorUtils.hasText(definition.getId())) {
            cache.putIfAbsent(IndicatorUtils.normalize(definition.getId()), built);
        }

        return built;
    }

    public Indicator<Num> resolveInput(
            Object inputDefinition,
            BarSeries series,
            Map<String, Indicator<Num>> cache,
            String fallbackReference
    ) {
        Object candidate = inputDefinition == null ? fallbackReference : inputDefinition;
        if (candidate == null) {
            throw new InvalidStrategyConfigurationException("Indicator input reference must be provided");
        }

        if (candidate instanceof String reference) {
            Indicator<Num> resolved = cache.get(IndicatorUtils.normalize(reference));
            if (resolved == null) {
                throw new InvalidStrategyConfigurationException("Unknown indicator reference: " + reference);
            }
            return resolved;
        }

        if (candidate instanceof IndicatorDefinition nestedDefinition) {
            return createIndicator(nestedDefinition, series, cache);
        }

        if (candidate instanceof Map<?, ?> nestedMap) {
            IndicatorDefinition converted = objectMapper.convertValue(nestedMap, IndicatorDefinition.class);
            return createIndicator(converted, series, cache);
        }

        throw new InvalidStrategyConfigurationException("Unsupported indicator input type: " + candidate.getClass().getSimpleName());
    }

    private Indicator<Num> resolvePrimaryInput(
            IndicatorDefinition definition,
            BarSeries series,
            Map<String, Indicator<Num>> cache
    ) {
        Object directInput = definition.getInput() != null
                ? definition.getInput()
                : IndicatorUtils.getObject(definition, "input");
        return resolveInput(directInput, series, cache, "close");
    }

    private void registerBaseIndicators(BarSeries series, Map<String, Indicator<Num>> cache) {
        cache.put("open", new OpenPriceIndicator(series));
        cache.put("high", new HighPriceIndicator(series));
        cache.put("low", new LowPriceIndicator(series));
        cache.put("close", new ClosePriceIndicator(series));
        cache.put("volume", new VolumeIndicator(series));
    }

    private String computeCacheKey(IndicatorDefinition definition) {
        if (IndicatorUtils.hasText(definition.getId())) {
            return IndicatorUtils.normalize(definition.getId());
        }
        try {
            return "__inline__:" + objectMapper.writeValueAsString(definition);
        } catch (Exception exception) {
            return IndicatorUtils.cacheKey(definition);
        }
    }
}
