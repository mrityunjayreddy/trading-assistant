package com.tradingservice.tradingengine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndicatorDefinition {

    @NotBlank
    private String id;

    @NotBlank
    private String type;

    private String subType;

    @Builder.Default
    private Map<String, Object> params = new LinkedHashMap<>();

    private Object input;
}
