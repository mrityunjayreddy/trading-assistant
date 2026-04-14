package com.tradingservice.aiservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a strategy from strategy-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyRecord {

    private UUID id;
    private String name;
    private String source;        // BUILTIN, USER, LLM, EVOLVED
    private Map<String, Object> dsl;
    private boolean isActive;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getIndicators() {
        if (dsl == null) return List.of();
        Object indicators = dsl.get("indicators");
        if (indicators instanceof List) return (List<Map<String, Object>>) indicators;
        return List.of();
    }

    public String getEntry() {
        if (dsl == null) return "";
        Object entry = dsl.get("entry");
        return entry != null ? entry.toString() : "";
    }

    public String getExit() {
        if (dsl == null) return "";
        Object exit = dsl.get("exit");
        return exit != null ? exit.toString() : "";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRisk() {
        if (dsl == null) return Map.of();
        Object risk = dsl.get("risk");
        if (risk instanceof Map) return (Map<String, Object>) risk;
        return Map.of();
    }
}
