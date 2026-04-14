package com.tradingservice.tradingengine.indicator;

import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.ta4j.Ta4jMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.num.Num;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorFactoryTest {

    private final IndicatorFactory indicatorFactory = new IndicatorFactory(new IndicatorRegistry(), new ObjectMapper());
    private final Ta4jMapper ta4jMapper = new Ta4jMapper();

    @Test
    void shouldBuildNestedIndicatorsRecursivelyAndCacheNestedIds() {
        BarSeries series = series();
        IndicatorDefinition definition = IndicatorDefinition.builder()
                .id("emaRsi")
                .type("EMA")
                .params(Map.of("window", 10))
                .input(IndicatorDefinition.builder()
                        .id("nestedRsi")
                        .type("RSI")
                        .params(Map.of("period", 14))
                        .input("close")
                        .build())
                .build();

        Map<String, Indicator<Num>> indicators = indicatorFactory.buildIndicators(List.of(definition), series);

        assertInstanceOf(RSIIndicator.class, indicators.get("nestedrsi"));
        assertInstanceOf(EMAIndicator.class, indicators.get("emarsi"));
        assertSame(indicators.get("nestedrsi"), indicatorFactory.createIndicator((IndicatorDefinition) definition.getInput(), series, indicators));
    }

    @Test
    void shouldCacheBollingerAliasesAndMacdDerivedSeries() {
        BarSeries series = series();
        Map<String, Indicator<Num>> indicators = indicatorFactory.buildIndicators(List.of(
                IndicatorDefinition.builder()
                        .id("bb")
                        .type("BOLLINGER")
                        .params(Map.of("window", 20, "stdDevMultiplier", 2))
                        .input("close")
                        .build(),
                IndicatorDefinition.builder()
                        .id("macd")
                        .type("MACD")
                        .params(Map.of("shortPeriod", 12, "longPeriod", 26, "signalPeriod", 9))
                        .input("close")
                        .build()
        ), series);

        assertInstanceOf(BollingerBandsMiddleIndicator.class, indicators.get("bb"));
        assertInstanceOf(BollingerBandsMiddleIndicator.class, indicators.get("bb.middle"));
        assertInstanceOf(BollingerBandsUpperIndicator.class, indicators.get("bb.upper"));
        assertInstanceOf(BollingerBandsLowerIndicator.class, indicators.get("bb.lower"));
        assertInstanceOf(MACDIndicator.class, indicators.get("macd"));
        assertNotNull(indicators.get("macd.signal"));
        assertNotNull(indicators.get("macd.histogram"));
    }

    @Test
    void shouldSupportCategoryTypeWithSubtype() {
        BarSeries series = series();
        Map<String, Indicator<Num>> indicators = indicatorFactory.buildIndicators(List.of(
                IndicatorDefinition.builder()
                        .id("hybridMacd")
                        .type("HYBRID")
                        .subType("MACD")
                        .params(Map.of("shortPeriod", 8, "longPeriod", 21))
                        .input("close")
                        .build()
        ), series);

        assertTrue(indicators.containsKey("hybridmacd"));
        assertInstanceOf(MACDIndicator.class, indicators.get("hybridmacd"));
    }

    private BarSeries series() {
        return ta4jMapper.mapToSeries(List.of(
                Kline.builder().openTime(1_000L).open(100).high(110).low(90).close(105).volume(1000).build(),
                Kline.builder().openTime(61_000L).open(105).high(115).low(95).close(108).volume(950).build(),
                Kline.builder().openTime(121_000L).open(108).high(118).low(101).close(112).volume(980).build(),
                Kline.builder().openTime(181_000L).open(112).high(120).low(109).close(118).volume(1020).build(),
                Kline.builder().openTime(241_000L).open(118).high(122).low(114).close(116).volume(1010).build(),
                Kline.builder().openTime(301_000L).open(116).high(124).low(111).close(121).volume(1005).build(),
                Kline.builder().openTime(361_000L).open(121).high(128).low(117).close(126).volume(1100).build(),
                Kline.builder().openTime(421_000L).open(126).high(130).low(120).close(122).volume(1040).build(),
                Kline.builder().openTime(481_000L).open(122).high(127).low(118).close(124).volume(990).build(),
                Kline.builder().openTime(541_000L).open(124).high(132).low(121).close(129).volume(1120).build(),
                Kline.builder().openTime(601_000L).open(129).high(136).low(125).close(134).volume(1200).build(),
                Kline.builder().openTime(661_000L).open(134).high(139).low(130).close(137).volume(1190).build(),
                Kline.builder().openTime(721_000L).open(137).high(141).low(133).close(135).volume(1180).build(),
                Kline.builder().openTime(781_000L).open(135).high(140).low(131).close(138).volume(1170).build(),
                Kline.builder().openTime(841_000L).open(138).high(144).low(134).close(142).volume(1250).build(),
                Kline.builder().openTime(901_000L).open(142).high(147).low(138).close(145).volume(1300).build(),
                Kline.builder().openTime(961_000L).open(145).high(149).low(140).close(143).volume(1280).build(),
                Kline.builder().openTime(1_021_000L).open(143).high(150).low(139).close(148).volume(1310).build(),
                Kline.builder().openTime(1_081_000L).open(148).high(152).low(144).close(150).volume(1295).build(),
                Kline.builder().openTime(1_141_000L).open(150).high(155).low(146).close(153).volume(1330).build(),
                Kline.builder().openTime(1_201_000L).open(153).high(158).low(149).close(156).volume(1345).build(),
                Kline.builder().openTime(1_261_000L).open(156).high(160).low(151).close(154).volume(1320).build(),
                Kline.builder().openTime(1_321_000L).open(154).high(162).low(150).close(159).volume(1375).build(),
                Kline.builder().openTime(1_381_000L).open(159).high(165).low(155).close(163).volume(1400).build(),
                Kline.builder().openTime(1_441_000L).open(163).high(168).low(158).close(166).volume(1425).build(),
                Kline.builder().openTime(1_501_000L).open(166).high(170).low(160).close(164).volume(1390).build(),
                Kline.builder().openTime(1_561_000L).open(164).high(171).low(161).close(169).volume(1435).build()
        ));
    }
}
