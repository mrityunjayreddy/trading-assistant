package com.trading.marketdata;

import com.trading.marketdata.normalizer.BinanceTradeNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "marketdata.stream.auto-start=false")
class MarketDataApplicationTests {

    @Autowired
    private BinanceTradeNormalizer binanceTradeNormalizer;

    @Test
    void contextLoads() {
        assertThat(binanceTradeNormalizer).isNotNull();
    }
}
