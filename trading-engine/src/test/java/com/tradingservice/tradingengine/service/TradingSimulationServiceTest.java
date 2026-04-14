package com.tradingservice.tradingengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.tradingservice.tradingengine.config.SimulationProperties;
import com.tradingservice.tradingengine.dto.ExecutionModelRequest;
import com.tradingservice.tradingengine.dto.ExecutionParameters;
import com.tradingservice.tradingengine.dto.IndicatorDefinition;
import com.tradingservice.tradingengine.dto.RuleComparator;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.dto.SimulationAssumptions;
import com.tradingservice.tradingengine.dto.SimulationRange;
import com.tradingservice.tradingengine.dto.SimulationRequest;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.indicator.IndicatorFactory;
import com.tradingservice.tradingengine.indicator.IndicatorRegistry;
import com.tradingservice.tradingengine.model.ExecutionModelType;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.model.TradeDirection;
import com.tradingservice.tradingengine.ta4j.MetricService;
import com.tradingservice.tradingengine.ta4j.RuleFactory;
import com.tradingservice.tradingengine.ta4j.StrategyDefinitionResolver;
import com.tradingservice.tradingengine.ta4j.Ta4jMapper;
import com.tradingservice.tradingengine.ta4j.Ta4jStrategyBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TradingSimulationServiceTest {

    @Mock
    private HistoricalDataService historicalDataService;

    private TradingSimulationService tradingSimulationService;

    @BeforeEach
    void setUp() {
        SimulationProperties props = new SimulationProperties();
        props.setInitialBalance(1000.0);
        props.setFeeRate(0.0004);

        BacktestingService backtestingService = new BacktestingService(
                props,
                new Ta4jStrategyBuilder(new IndicatorFactory(new IndicatorRegistry(), new ObjectMapper()), new RuleFactory()),
                new MetricService()
        );

        tradingSimulationService = new TradingSimulationService(
                historicalDataService,
                new StrategyDefinitionResolver(),
                new Ta4jMapper(),
                backtestingService
        );
    }

    // ── MA Crossover ──────────────────────────────────────────────────────────

    @Test
    void shouldRunMaCrossoverSimulationAndReturnResult() {
        List<Kline> klines = buildKlines(100, 100, 100, 200, 200, 200, 100, 100);
        stubHistoricalData(klines);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .strategy("MA_CROSSOVER")
                .params(Map.of("shortWindow", 2, "longWindow", 3))
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build())
                .range(SimulationRange.builder().startTime(1000L).endTime(2000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .assertNext(result -> {
                    // Structural invariants hold regardless of whether the crossover fires
                    assertThat(result.candles()).hasSize(klines.size());
                    assertThat(result.equityCurve()).hasSize(klines.size());
                    assertThat(result.initialBalance()).isEqualTo(1000.0);
                    assertThat(result.finalBalance()).isPositive();
                })
                .verifyComplete();
    }

    @Test
    void shouldNormaliseCaseForSymbolAndInterval() {
        List<Kline> klines = buildKlines(100, 200);
        // Service normalises: symbol → UPPERCASE, interval → lowercase
        when(historicalDataService.fetchHistoricalKlines(
                eq("BTCUSDT"), eq("1h"), anyLong(), anyLong(), isNull()
        )).thenReturn(Mono.just(klines));

        SimulationRequest request = SimulationRequest.builder()
                .symbol("btcusdt")   // lower-case input — must be normalised to "BTCUSDT"
                .interval("1H")      // upper-case input — must be normalised to "1h"
                .strategy("BUY_AND_HOLD")
                .params(Map.of())
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().build())
                .range(SimulationRange.builder().startTime(1000L).endTime(2000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .assertNext(result -> assertThat(result.candles()).hasSize(2))
                .verifyComplete();
    }

    // ── RSI strategy ─────────────────────────────────────────────────────────

    @Test
    void shouldRunRsiSimulationWithoutError() {
        // 20 candles with varied prices to allow RSI to produce signals
        List<Kline> klines = buildKlines(
                100, 90, 80, 70, 60, 70, 80, 90, 100, 110,
                120, 110, 100, 90, 80, 90, 100, 110, 120, 130
        );
        stubHistoricalData(klines);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("ETHUSDT")
                .interval("1h")
                .strategy("RSI")
                .params(Map.of("period", 3, "oversold", 40.0, "overbought", 60.0))
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0004).build())
                .range(SimulationRange.builder().startTime(1000L).endTime(9000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .assertNext(result -> {
                    assertThat(result.candles()).hasSize(klines.size());
                    assertThat(result.equityCurve()).hasSize(klines.size());
                    assertThat(result.initialBalance()).isEqualTo(1000.0);
                    assertThat(result.finalBalance()).isPositive();
                })
                .verifyComplete();
    }

    // ── DSL strategy ─────────────────────────────────────────────────────────

    @Test
    void shouldRunSimulationWithCustomDslStrategy() {
        // DSL: always enter (close > 0), never exit (close < -1)
        // Expects 1 open trade that never closes → position held at end of period
        List<Kline> klines = buildKlines(100, 110, 120, 130);
        stubHistoricalData(klines);

        IndicatorDefinition sma = IndicatorDefinition.builder()
                .id("ma")
                .type("SMA")
                .params(Map.of("window", 2))
                .input("close")
                .build();

        RuleDefinition alwaysEntry = RuleDefinition.builder()
                .left("close")
                .operator(RuleComparator.GREATER_THAN)
                .rightValue(0.0)
                .build();
        RuleDefinition neverExit = RuleDefinition.builder()
                .left("close")
                .operator(RuleComparator.LESS_THAN)
                .rightValue(-1.0)
                .build();

        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("5m")
                .strategy("MA_CROSSOVER")       // name is ignored when DSL fields are present
                .params(Map.of())
                .indicators(List.of(sma))
                .entryRules(alwaysEntry)
                .exitRules(neverExit)
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build())
                .range(SimulationRange.builder().startTime(1000L).endTime(5000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .assertNext(result -> {
                    // Position opened but never closed → only 1 trade (OPEN_LONG)
                    assertThat(result.trades()).hasSize(1);
                    assertThat(result.trades().get(0).executionType())
                            .isEqualTo(com.tradingservice.tradingengine.model.TradeExecutionType.OPEN_LONG);
                    // Final balance accounts for the open position at last close price (130)
                    assertThat(result.finalBalance()).isGreaterThan(1000.0);
                })
                .verifyComplete();
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void shouldErrorWhenHistoricalDataIsEmpty() {
        when(historicalDataService.fetchHistoricalKlines(anyString(), anyString(), anyLong(), anyLong(), isNull()))
                .thenReturn(Mono.just(List.of()));

        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .strategy("BUY_AND_HOLD")
                .params(Map.of())
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().build())
                .range(SimulationRange.builder().startTime(1000L).endTime(2000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .expectErrorMatches(error ->
                        error instanceof InvalidStrategyConfigurationException
                        && error.getMessage().contains("No kline data")
                )
                .verify();
    }

    @Test
    void shouldThrowSynchronouslyWhenRangeStartIsAfterEnd() {
        // validateRange() throws before the Mono is constructed, so assertThatThrownBy is correct here
        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .strategy("MA_CROSSOVER")
                .params(Map.of("shortWindow", 5, "longWindow", 20))
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().build())
                .range(SimulationRange.builder().startTime(9000L).endTime(1000L).build())
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> tradingSimulationService.simulate(request))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("start time must be before end time");
    }

    @Test
    void shouldThrowSynchronouslyWhenRangeStartIsNegative() {
        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .strategy("BUY_AND_HOLD")
                .params(Map.of())
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().build())
                .range(SimulationRange.builder().startTime(-1L).endTime(1000L).build())
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> tradingSimulationService.simulate(request))
                .isInstanceOf(InvalidStrategyConfigurationException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldErrorWhenHistoricalDataServiceFails() {
        when(historicalDataService.fetchHistoricalKlines(anyString(), anyString(), anyLong(), anyLong(), isNull()))
                .thenReturn(Mono.error(new RuntimeException("Binance unreachable")));

        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .strategy("BUY_AND_HOLD")
                .params(Map.of())
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().build())
                .range(SimulationRange.builder().startTime(1000L).endTime(2000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .expectErrorMatches(error -> error.getMessage().contains("Binance unreachable"))
                .verify();
    }

    // ── Result structure ──────────────────────────────────────────────────────

    @Test
    void shouldReturnEquityCurveAlignedWithCandles() {
        List<Kline> klines = buildKlines(100, 110, 120, 130, 140);
        stubHistoricalData(klines);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1d")
                .strategy("BUY_AND_HOLD")
                .params(Map.of())
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build())
                .range(SimulationRange.builder().startTime(1000L).endTime(9000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .assertNext(result -> {
                    assertThat(result.equityCurve()).hasSize(result.candles().size());
                    // BUY_AND_HOLD buys at index 0 (price=100), equity grows with price
                    // At last candle (price=140): 10 units * 140 = 1400
                    assertThat(result.equityCurve().get(4).equity()).isEqualTo(1400.0);
                    assertThat(result.equityCurve().get(4).closePrice()).isEqualTo(140.0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnAllKlinesInResult() {
        List<Kline> klines = buildKlines(50, 60, 70, 80, 90, 100);
        stubHistoricalData(klines);

        SimulationRequest request = SimulationRequest.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .strategy("BUY_AND_HOLD")
                .params(Map.of())
                .execution(fullBalanceExecution())
                .assumptions(SimulationAssumptions.builder().build())
                .range(SimulationRange.builder().startTime(1000L).endTime(9000L).build())
                .build();

        StepVerifier.create(tradingSimulationService.simulate(request))
                .assertNext(result -> assertThat(result.candles()).isEqualTo(klines))
                .verifyComplete();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubHistoricalData(List<Kline> klines) {
        when(historicalDataService.fetchHistoricalKlines(anyString(), anyString(), anyLong(), anyLong(), isNull()))
                .thenReturn(Mono.just(klines));
    }

    private List<Kline> buildKlines(double... prices) {
        List<Kline> klines = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            klines.add(Kline.builder()
                    .openTime(i + 1L)
                    .open(prices[i])
                    .high(prices[i])
                    .low(prices[i])
                    .close(prices[i])
                    .volume(1000)
                    .build());
        }
        return klines;
    }

    private ExecutionModelRequest fullBalanceExecution() {
        return ExecutionModelRequest.builder()
                .type(ExecutionModelType.FULL_BALANCE)
                .params(ExecutionParameters.builder().build())
                .tradeDirection(TradeDirection.LONG_ONLY)
                .build();
    }
}