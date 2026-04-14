package com.tradingservice.tradingengine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradingservice.tradingengine.config.SimulationProperties;
import com.tradingservice.tradingengine.dto.ExecutionParameters;
import com.tradingservice.tradingengine.dto.ExecutionModelRequest;
import com.tradingservice.tradingengine.dto.RuleComparator;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.dto.SimulationAssumptions;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.model.ExecutionModelType;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.PositionSide;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.model.TradeDirection;
import com.tradingservice.tradingengine.model.TradeExecutionType;
import com.tradingservice.tradingengine.indicator.IndicatorFactory;
import com.tradingservice.tradingengine.indicator.IndicatorRegistry;
import com.tradingservice.tradingengine.ta4j.MetricService;
import com.tradingservice.tradingengine.ta4j.RuleFactory;
import com.tradingservice.tradingengine.ta4j.Ta4jMapper;
import com.tradingservice.tradingengine.ta4j.Ta4jStrategyBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class

BacktestingServiceTest {

    private final Ta4jMapper ta4jMapper = new Ta4jMapper();
    private final BacktestingService backtestingService = new BacktestingService(
            simulationProperties(),
            new Ta4jStrategyBuilder(new IndicatorFactory(new IndicatorRegistry(), new ObjectMapper()), new RuleFactory()),
            new MetricService()
    );

    @Test
    void shouldSupportShortOnlyBacktests() {
        List<Kline> klines = List.of(kline(1, 100), kline(2, 90));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                shortOnlyDefinition(),
                executionRequest(TradeDirection.SHORT_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        assertThat(result.finalBalance()).isEqualTo(1100.0);
        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().get(0).executionType()).isEqualTo(TradeExecutionType.OPEN_SHORT);
        assertThat(result.trades().get(1).executionType()).isEqualTo(TradeExecutionType.CLOSE_SHORT);
        assertThat(result.trades().get(1).realizedPnl()).isEqualTo(100.0);
    }

    @Test
    void shouldReverseFromLongToShortInBidirectionalMode() {
        List<Kline> klines = List.of(kline(1, 100), kline(2, 110), kline(3, 90));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                longToShortDefinition(),
                executionRequest(TradeDirection.BOTH),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        assertThat(result.trades()).extracting(trade -> trade.executionType())
                .containsExactly(
                        TradeExecutionType.OPEN_LONG,
                        TradeExecutionType.CLOSE_LONG,
                        TradeExecutionType.OPEN_SHORT
                );
        assertThat(result.trades()).extracting(trade -> trade.positionSide())
                .contains(PositionSide.LONG, PositionSide.SHORT);
        assertThat(result.finalBalance()).isEqualTo(900.0);
    }

    private StrategyDefinition shortOnlyDefinition() {
        return StrategyDefinition.builder()
                .indicators(List.of())
                .entryRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.LESS_THAN)
                        .rightValue(95.0)
                        .build())
                .exitRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.GREATER_THAN)
                        .rightValue(0.0)
                        .build())
                .build();
    }

    private StrategyDefinition longToShortDefinition() {
        return StrategyDefinition.builder()
                .indicators(List.of())
                .entryRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.GREATER_THAN)
                        .rightValue(0.0)
                        .build())
                .exitRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.LESS_THAN)
                        .rightValue(95.0)
                        .build())
                .build();
    }

    private ExecutionModelRequest executionRequest(TradeDirection tradeDirection) {
        return ExecutionModelRequest.builder()
                .type(ExecutionModelType.FULL_BALANCE)
                .params(ExecutionParameters.builder().build())
                .tradeDirection(tradeDirection)
                .build();
    }

    private Kline kline(long openTime, double close) {
        return Kline.builder()
                .openTime(openTime)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(100)
                .build();
    }

    @Test
    void shouldProduceLongOnlyProfitableRound() {
        // Entry when close > 50 (fires at index 0), exit when close > 150 (fires at index 3)
        List<Kline> klines = List.of(kline(1, 100), kline(2, 100), kline(3, 100), kline(4, 200));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                alwaysEnterExitAbove150Definition(),
                executionRequest(TradeDirection.LONG_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        // Buy 10 units at 100, sell at 200 → final balance doubles
        assertThat(result.finalBalance()).isEqualTo(2000.0);
        assertThat(result.totalReturn()).isEqualTo(100.0);
        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().get(0).executionType()).isEqualTo(TradeExecutionType.OPEN_LONG);
        assertThat(result.trades().get(0).price()).isEqualTo(100.0);
        assertThat(result.trades().get(1).executionType()).isEqualTo(TradeExecutionType.CLOSE_LONG);
        assertThat(result.trades().get(1).price()).isEqualTo(200.0);
        assertThat(result.trades().get(1).realizedPnl()).isEqualTo(1000.0);
    }

    @Test
    void shouldDeductFeesFromProfitOnClose() {
        // Same trade as above but with 1% fee on each side
        List<Kline> klines = List.of(kline(1, 100), kline(2, 100), kline(3, 100), kline(4, 200));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                alwaysEnterExitAbove150Definition(),
                executionRequest(TradeDirection.LONG_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.01).build()
        );

        // Buy: 1000 * 0.99 / 100 = 9.9 units, cash = 0
        // Sell: 9.9 * 200 * 0.99 = 1960.2
        assertThat(result.finalBalance()).isLessThan(2000.0);
        assertThat(result.finalBalance()).isGreaterThan(1000.0);
        assertThat(result.totalReturn()).isGreaterThan(0.0);
        assertThat(result.trades()).hasSize(2);
    }

    @Test
    void shouldProduceNoTradesWhenStrategyNeverSignals() {
        // Entry rule never fires: close > 99999
        List<Kline> klines = List.of(kline(1, 100), kline(2, 110), kline(3, 120));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                neverEntryDefinition(),
                executionRequest(TradeDirection.LONG_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        assertThat(result.trades()).isEmpty();
        assertThat(result.finalBalance()).isEqualTo(1000.0);
        assertThat(result.totalReturn()).isEqualTo(0.0);
    }

    @Test
    void shouldBuildEquityCurveWithOnePointPerKline() {
        List<Kline> klines = List.of(kline(1, 100), kline(2, 100), kline(3, 100), kline(4, 200));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                alwaysEnterExitAbove150Definition(),
                executionRequest(TradeDirection.LONG_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        assertThat(result.equityCurve()).hasSize(klines.size());
        // After entry at index 0 (price=100), mark-to-market equity stays at 1000 while price holds
        assertThat(result.equityCurve().get(0).equity()).isEqualTo(1000.0);
        assertThat(result.equityCurve().get(1).equity()).isEqualTo(1000.0);
        // After exit at index 3 (price=200), equity reflects the closed cash balance
        assertThat(result.equityCurve().get(3).equity()).isEqualTo(2000.0);
    }

    @Test
    void shouldReturnAllInputCandlesInResult() {
        List<Kline> klines = List.of(kline(1, 100), kline(2, 110), kline(3, 120), kline(4, 130), kline(5, 140));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                neverEntryDefinition(),
                executionRequest(TradeDirection.LONG_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        assertThat(result.candles()).hasSize(klines.size());
        assertThat(result.candles()).isEqualTo(klines);
    }

    @Test
    void shouldAllocatePercentOfBalanceOnEntry() {
        // 50% allocation: buys 5 units at 100, sells at 200 → 50% gain on initial capital
        List<Kline> klines = List.of(kline(1, 100), kline(2, 100), kline(3, 100), kline(4, 200));
        ExecutionModelRequest percentModel = ExecutionModelRequest.builder()
                .type(ExecutionModelType.PERCENT_OF_BALANCE)
                .params(ExecutionParameters.builder().allocationPercent(50.0).build())
                .tradeDirection(TradeDirection.LONG_ONLY)
                .build();

        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                alwaysEnterExitAbove150Definition(),
                percentModel,
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        // Buy 5 units at 100 (500 allocated), remaining 500 cash
        // Sell 5 units at 200 = 1000 proceeds, total = 500 + 1000 = 1500
        assertThat(result.finalBalance()).isEqualTo(1500.0);
        assertThat(result.totalReturn()).isEqualTo(50.0);
    }

    @Test
    void shouldAllocateFixedAmountOnEntry() {
        // Fixed 200 USDT: buys 2 units at 100, sells at 200
        List<Kline> klines = List.of(kline(1, 100), kline(2, 100), kline(3, 100), kline(4, 200));
        ExecutionModelRequest fixedModel = ExecutionModelRequest.builder()
                .type(ExecutionModelType.FIXED_AMOUNT)
                .params(ExecutionParameters.builder().fixedAmount(200.0).build())
                .tradeDirection(TradeDirection.LONG_ONLY)
                .build();

        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                alwaysEnterExitAbove150Definition(),
                fixedModel,
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        // Buy 2 units at 100 (200 allocated), 800 remaining
        // Sell 2 units at 200 = 400 proceeds, total = 800 + 400 = 1200
        assertThat(result.finalBalance()).isEqualTo(1200.0);
        assertThat(result.totalReturn()).isEqualTo(20.0);
    }

    @Test
    void shouldThrowWhenKlineListIsEmpty() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException.class,
                () -> backtestingService.run(
                        List.of(),
                        ta4jMapper.mapToSeries(List.of(kline(1, 100))),
                        neverEntryDefinition(),
                        executionRequest(TradeDirection.LONG_ONLY),
                        SimulationAssumptions.builder().build()
                )
        );
    }

    @Test
    void shouldRecordCashBalanceAfterEachTrade() {
        List<Kline> klines = List.of(kline(1, 100), kline(2, 100), kline(3, 100), kline(4, 200));
        SimulationResult result = backtestingService.run(
                klines,
                ta4jMapper.mapToSeries(klines),
                alwaysEnterExitAbove150Definition(),
                executionRequest(TradeDirection.LONG_ONLY),
                SimulationAssumptions.builder().initialBalance(1000.0).feeRate(0.0).build()
        );

        // After BUY: all cash deployed, balance = 0
        assertThat(result.trades().get(0).cashBalanceAfter()).isEqualTo(0.0);
        // After SELL: proceeds returned, balance = 2000
        assertThat(result.trades().get(1).cashBalanceAfter()).isEqualTo(2000.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private StrategyDefinition alwaysEnterExitAbove150Definition() {
        return StrategyDefinition.builder()
                .indicators(List.of())
                .entryRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.GREATER_THAN)
                        .rightValue(50.0)
                        .build())
                .exitRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.GREATER_THAN)
                        .rightValue(150.0)
                        .build())
                .build();
    }

    private StrategyDefinition neverEntryDefinition() {
        return StrategyDefinition.builder()
                .indicators(List.of())
                .entryRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.GREATER_THAN)
                        .rightValue(99_999.0)
                        .build())
                .exitRules(RuleDefinition.builder()
                        .left("close")
                        .operator(RuleComparator.GREATER_THAN)
                        .rightValue(0.0)
                        .build())
                .build();
    }

    private SimulationProperties simulationProperties() {
        SimulationProperties properties = new SimulationProperties();
        properties.setInitialBalance(1000.0);
        properties.setFeeRate(0.0004);
        return properties;
    }
}
