package com.tradingservice.strategyservice.seeder;

import com.tradingservice.strategyservice.domain.IndicatorConfig;
import com.tradingservice.strategyservice.domain.RiskConfig;
import com.tradingservice.strategyservice.domain.StrategyDSL;
import com.tradingservice.strategyservice.entity.StrategyRecord;
import com.tradingservice.strategyservice.repository.StrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds 5 BUILTIN strategies on startup if the {@code strategies} table
 * contains zero BUILTIN rows.
 *
 * <p>Uses {@link ApplicationRunner} so JPA is fully initialized before seeding.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategySeeder implements ApplicationRunner {

    private final StrategyRepository strategyRepository;

    @Override
    public void run(ApplicationArguments args) {
        long count = strategyRepository.countBySourceAndIsActiveTrue("BUILTIN");
        if (count > 0) {
            log.info("StrategySeeder: {} BUILTIN strategies already present — skipping seed", count);
            return;
        }

        log.info("StrategySeeder: seeding 5 BUILTIN strategies...");
        builtinStrategies().forEach(dsl -> {
            StrategyRecord record = StrategyRecord.builder()
                    .name(dsl.name())
                    .source("BUILTIN")
                    .dsl(dsl)
                    .isActive(true)
                    .build();
            strategyRepository.save(record);
            log.info("  Seeded BUILTIN strategy: {}", dsl.name());
        });
        log.info("StrategySeeder: seeding complete");
    }

    // -------------------------------------------------------------------------

    private List<StrategyDSL> builtinStrategies() {
        return List.of(
                goldenCross(),
                rsiMeanReversion(),
                bbBreakout(),
                macdMomentum(),
                emaRsiCombo()
        );
    }

    // ---- Golden Cross (SMA 50 / SMA 200) ------------------------------------

    private StrategyDSL goldenCross() {
        return new StrategyDSL(
                "Golden Cross",
                "1.0",
                "BUILTIN",
                List.of(
                        new IndicatorConfig("sma50",  "SMA", Map.of("period", 50)),
                        new IndicatorConfig("sma200", "SMA", Map.of("period", 200))
                ),
                "cross_above(sma50, sma200)",
                "cross_below(sma50, sma200)",
                new RiskConfig(3.0, 6.0, 10.0, false)
        );
    }

    // ---- RSI Mean Reversion (RSI 14, oversold/overbought) -------------------

    private StrategyDSL rsiMeanReversion() {
        return new StrategyDSL(
                "RSI Mean Reversion",
                "1.0",
                "BUILTIN",
                List.of(
                        new IndicatorConfig("rsi14", "RSI", Map.of("period", 14))
                ),
                "rsi14 < 30",
                "rsi14 > 70",
                new RiskConfig(2.0, 4.0, 8.0, false)
        );
    }

    // ---- Bollinger Band Breakout --------------------------------------------

    private StrategyDSL bbBreakout() {
        return new StrategyDSL(
                "Bollinger Band Breakout",
                "1.0",
                "BUILTIN",
                List.of(
                        new IndicatorConfig("bb20", "BOLLINGER_BANDS", Map.of("period", 20, "stdDev", 2.0)),
                        new IndicatorConfig("sma20", "SMA", Map.of("period", 20))
                ),
                "cross_above(sma20, bb20.upper)",
                "cross_below(sma20, bb20.lower)",
                new RiskConfig(2.5, 5.0, 10.0, true)
        );
    }

    // ---- MACD Momentum -------------------------------------------------------

    private StrategyDSL macdMomentum() {
        return new StrategyDSL(
                "MACD Momentum",
                "1.0",
                "BUILTIN",
                List.of(
                        new IndicatorConfig("macd", "MACD", Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9))
                ),
                "cross_above(macd.macd, macd.signal)",
                "cross_below(macd.macd, macd.signal)",
                new RiskConfig(3.0, 9.0, 10.0, false)
        );
    }

    // ---- EMA + RSI Combo ----------------------------------------------------

    private StrategyDSL emaRsiCombo() {
        return new StrategyDSL(
                "EMA RSI Combo",
                "1.0",
                "BUILTIN",
                List.of(
                        new IndicatorConfig("ema9",  "EMA", Map.of("period", 9)),
                        new IndicatorConfig("ema21", "EMA", Map.of("period", 21)),
                        new IndicatorConfig("rsi14", "RSI", Map.of("period", 14))
                ),
                "cross_above(ema9, ema21) AND rsi14 > 50",
                "cross_below(ema9, ema21) OR rsi14 < 40",
                new RiskConfig(2.0, 6.0, 10.0, false)
        );
    }
}