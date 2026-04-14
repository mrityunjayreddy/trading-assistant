package com.tradingservice.tradingengine.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GridGeneratorTest {

    private final GridGenerator gridGenerator = new GridGenerator();

    @Test
    void shouldGenerateCartesianProductOfParameterGrid() {
        List<Map<String, Object>> combinations = gridGenerator.generate(Map.of(
                "shortWindow", List.of(5, 10),
                "longWindow", List.of(30, 50)
        ));

        assertThat(combinations).hasSize(4);
        assertThat(combinations).contains(
                Map.of("shortWindow", 5, "longWindow", 30),
                Map.of("shortWindow", 5, "longWindow", 50),
                Map.of("shortWindow", 10, "longWindow", 30),
                Map.of("shortWindow", 10, "longWindow", 50)
        );
    }
}
