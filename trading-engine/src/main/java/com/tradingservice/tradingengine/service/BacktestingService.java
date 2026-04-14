package com.tradingservice.tradingengine.service;

import com.tradingservice.tradingengine.config.SimulationProperties;
import com.tradingservice.tradingengine.dto.ExecutionModelRequest;
import com.tradingservice.tradingengine.dto.SimulationAssumptions;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.model.EquityPoint;
import com.tradingservice.tradingengine.model.ExecutedTrade;
import com.tradingservice.tradingengine.model.ExecutionModelType;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.PositionSide;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.model.TradeAction;
import com.tradingservice.tradingengine.model.TradeDirection;
import com.tradingservice.tradingengine.model.TradeExecutionType;
import com.tradingservice.tradingengine.ta4j.MetricService;
import com.tradingservice.tradingengine.ta4j.Ta4jStrategyBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestingService {

    private final SimulationProperties simulationProperties;
    private final Ta4jStrategyBuilder ta4jStrategyBuilder;
    private final MetricService metricService;

    public SimulationResult run(
            List<Kline> klines,
            BarSeries series,
            StrategyDefinition definition,
            ExecutionModelRequest executionModel,
            SimulationAssumptions assumptions
    ) {
        return runDetailed(klines, series, definition, executionModel, assumptions).simulationResult();
    }

    public BacktestExecution runDetailed(
            List<Kline> klines,
            BarSeries series,
            StrategyDefinition definition,
            ExecutionModelRequest executionModel,
            SimulationAssumptions assumptions
    ) {
        if (klines.isEmpty()) {
            throw new InvalidStrategyConfigurationException("At least one kline is required to run a backtest");
        }

        double initialBalance = resolveInitialBalance(assumptions);
        double feeRate = resolveFeeRate(assumptions);
        TradeDirection tradeDirection = executionModel.getTradeDirection();

        Strategy longStrategy = ta4jStrategyBuilder.build(definition, series);
        Strategy shortStrategy = ta4jStrategyBuilder.buildInverse(definition, series);
        PortfolioState state = new PortfolioState(initialBalance);
        List<ExecutedTrade> trades = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>(klines.size());
        BaseTradingRecord longRecord = new BaseTradingRecord();
        BaseTradingRecord shortRecord = new BaseTradingRecord(org.ta4j.core.Trade.TradeType.SELL);

        for (int index = 0; index < klines.size(); index++) {
            Kline kline = klines.get(index);
            evaluateBar(
                    index,
                    series,
                    executionModel,
                    feeRate,
                    tradeDirection,
                    longStrategy,
                    shortStrategy,
                    longRecord,
                    shortRecord,
                    state,
                    trades,
                    kline
            );

            equityCurve.add(EquityPoint.builder()
                    .timestamp(kline.openTime())
                    .equity(round(markToMarketEquity(state, kline.close())))
                    .closePrice(round(kline.close()))
                    .build());
        }

        double lastClose = klines.get(klines.size() - 1).close();
        double finalBalance = calculateClosingEquity(state, lastClose, feeRate);
        double totalReturn = ((finalBalance - initialBalance) / initialBalance) * 100;
        TradingRecord metricRecord = longRecord.getPositionCount() >= shortRecord.getPositionCount() ? longRecord : shortRecord;
        int ta4jTradeCount = metricService.numberOfTrades(series, metricRecord) + metricService.numberOfTrades(series, longRecord == metricRecord ? shortRecord : longRecord);
        double totalProfit = ((finalBalance - initialBalance) / initialBalance);
        double maximumDrawdown = metricService.maximumDrawdown(series, metricRecord);
        double averageProfit = metricService.averageProfit(series, metricRecord);

        log.info(
                "Backtest complete direction={} initialBalance={} finalBalance={} totalReturn={} executions={} ta4jTrades={} totalProfit={} maxDrawdown={} averageProfit={}",
                tradeDirection,
                initialBalance,
                finalBalance,
                totalReturn,
                trades.size(),
                ta4jTradeCount,
                round(totalProfit),
                round(maximumDrawdown),
                round(averageProfit)
        );

        SimulationResult simulationResult = SimulationResult.builder()
                .initialBalance(round(initialBalance))
                .finalBalance(round(finalBalance))
                .totalReturn(round(totalReturn))
                .tradesCount(trades.size())
                .candles(klines)
                .trades(trades)
                .equityCurve(equityCurve)
                .build();

        return BacktestExecution.builder()
                .simulationResult(simulationResult)
                .record(metricRecord)
                .totalProfit(round(totalProfit))
                .maximumDrawdown(round(maximumDrawdown))
                .averageProfit(round(averageProfit))
                .numberOfTrades(ta4jTradeCount)
                .build();
    }

    private void evaluateBar(
            int index,
            BarSeries series,
            ExecutionModelRequest executionModel,
            double feeRate,
            TradeDirection tradeDirection,
            Strategy longStrategy,
            Strategy shortStrategy,
            BaseTradingRecord longRecord,
            BaseTradingRecord shortRecord,
            PortfolioState state,
            List<ExecutedTrade> trades,
            Kline kline
    ) {
        var closePrice = series.numFactory().numOf(kline.close());
        var tradeAmount = series.numFactory().one();

        if (state.positionSide == PositionSide.LONG) {
            if (!longStrategy.shouldExit(index, longRecord)) {
                return;
            }

            longRecord.exit(index, closePrice, tradeAmount);
            closeLongPosition(state, feeRate, trades, kline.openTime(), kline);
            if (tradeDirection == TradeDirection.BOTH && shortStrategy.shouldEnter(index, shortRecord)) {
                shortRecord.enter(index, closePrice, tradeAmount);
                openShortPosition(executionModel, state, feeRate, trades, kline.openTime(), kline);
            }
            return;
        }

        if (state.positionSide == PositionSide.SHORT) {
            if (!shortStrategy.shouldExit(index, shortRecord)) {
                return;
            }

            shortRecord.exit(index, closePrice, tradeAmount);
            closeShortPosition(state, feeRate, trades, kline.openTime(), kline);
            if (tradeDirection == TradeDirection.BOTH && longStrategy.shouldEnter(index, longRecord)) {
                longRecord.enter(index, closePrice, tradeAmount);
                openLongPosition(executionModel, state, feeRate, trades, kline.openTime(), kline);
            }
            return;
        }

        if (tradeDirection != TradeDirection.SHORT_ONLY && longStrategy.shouldEnter(index, longRecord)) {
            longRecord.enter(index, closePrice, tradeAmount);
            openLongPosition(executionModel, state, feeRate, trades, kline.openTime(), kline);
            return;
        }

        if (tradeDirection != TradeDirection.LONG_ONLY && shortStrategy.shouldEnter(index, shortRecord)) {
            shortRecord.enter(index, closePrice, tradeAmount);
            openShortPosition(executionModel, state, feeRate, trades, kline.openTime(), kline);
        }
    }

    private void openLongPosition(
            ExecutionModelRequest executionModel,
            PortfolioState state,
            double feeRate,
            List<ExecutedTrade> trades,
            long timestamp,
            Kline kline
    ) {
        if (state.positionSide != null) {
            return;
        }

        double allocationAmount = resolveAllocationAmount(executionModel, state.cashBalance);
        if (allocationAmount <= 0.0) {
            return;
        }

        double quantity = (allocationAmount * (1 - feeRate)) / kline.close();
        state.cashBalance -= allocationAmount;
        state.positionQuantity = quantity;
        state.positionSide = PositionSide.LONG;
        state.entryNotional = allocationAmount;
        trades.add(ExecutedTrade.builder()
                .timestamp(timestamp)
                .action(TradeAction.BUY)
                .positionSide(PositionSide.LONG)
                .executionType(TradeExecutionType.OPEN_LONG)
                .price(round(kline.close()))
                .quantity(round(quantity))
                .notional(round(allocationAmount))
                .realizedPnl(0.0)
                .cashBalanceAfter(round(state.cashBalance))
                .assetQuantityAfter(round(state.positionQuantity))
                .build());
    }

    private void closeLongPosition(
            PortfolioState state,
            double feeRate,
            List<ExecutedTrade> trades,
            long timestamp,
            Kline kline
    ) {
        if (state.positionSide != PositionSide.LONG) {
            return;
        }

        double grossProceeds = state.positionQuantity * kline.close();
        double netProceeds = grossProceeds * (1 - feeRate);
        double realizedPnl = netProceeds - state.entryNotional;
        state.cashBalance += netProceeds;
        state.positionQuantity = 0.0;
        state.positionSide = null;
        state.entryNotional = 0.0;
        trades.add(ExecutedTrade.builder()
                .timestamp(timestamp)
                .action(TradeAction.SELL)
                .positionSide(PositionSide.LONG)
                .executionType(TradeExecutionType.CLOSE_LONG)
                .price(round(kline.close()))
                .quantity(round(grossProceeds / kline.close()))
                .notional(round(grossProceeds))
                .realizedPnl(round(realizedPnl))
                .cashBalanceAfter(round(state.cashBalance))
                .assetQuantityAfter(0.0)
                .build());
    }

    private void openShortPosition(
            ExecutionModelRequest executionModel,
            PortfolioState state,
            double feeRate,
            List<ExecutedTrade> trades,
            long timestamp,
            Kline kline
    ) {
        if (state.positionSide != null) {
            return;
        }

        double allocationAmount = resolveAllocationAmount(executionModel, state.cashBalance);
        if (allocationAmount <= 0.0) {
            return;
        }

        double quantity = allocationAmount / kline.close();
        double netProceeds = allocationAmount * (1 - feeRate);
        state.cashBalance += netProceeds;
        state.positionQuantity = -quantity;
        state.positionSide = PositionSide.SHORT;
        state.entryNotional = allocationAmount;
        trades.add(ExecutedTrade.builder()
                .timestamp(timestamp)
                .action(TradeAction.SELL)
                .positionSide(PositionSide.SHORT)
                .executionType(TradeExecutionType.OPEN_SHORT)
                .price(round(kline.close()))
                .quantity(round(quantity))
                .notional(round(allocationAmount))
                .realizedPnl(0.0)
                .cashBalanceAfter(round(state.cashBalance))
                .assetQuantityAfter(round(state.positionQuantity))
                .build());
    }

    private void closeShortPosition(
            PortfolioState state,
            double feeRate,
            List<ExecutedTrade> trades,
            long timestamp,
            Kline kline
    ) {
        if (state.positionSide != PositionSide.SHORT) {
            return;
        }

        double quantity = Math.abs(state.positionQuantity);
        double grossCoverCost = quantity * kline.close();
        double totalCoverCost = grossCoverCost * (1 + feeRate);
        double realizedPnl = state.entryNotional - totalCoverCost;
        state.cashBalance -= totalCoverCost;
        state.positionQuantity = 0.0;
        state.positionSide = null;
        state.entryNotional = 0.0;
        trades.add(ExecutedTrade.builder()
                .timestamp(timestamp)
                .action(TradeAction.BUY)
                .positionSide(PositionSide.SHORT)
                .executionType(TradeExecutionType.CLOSE_SHORT)
                .price(round(kline.close()))
                .quantity(round(quantity))
                .notional(round(grossCoverCost))
                .realizedPnl(round(realizedPnl))
                .cashBalanceAfter(round(state.cashBalance))
                .assetQuantityAfter(0.0)
                .build());
    }

    private double markToMarketEquity(PortfolioState state, double price) {
        if (state.positionSide == null) {
            return state.cashBalance;
        }
        return state.cashBalance + (state.positionQuantity * price);
    }

    private double calculateClosingEquity(PortfolioState state, double lastClose, double feeRate) {
        if (state.positionSide == null) {
            return state.cashBalance;
        }
        if (state.positionSide == PositionSide.LONG) {
            return state.cashBalance + (state.positionQuantity * lastClose * (1 - feeRate));
        }
        return state.cashBalance - (Math.abs(state.positionQuantity) * lastClose * (1 + feeRate));
    }

    private double resolveInitialBalance(SimulationAssumptions assumptions) {
        Double initialBalance = assumptions.getInitialBalance();
        if (initialBalance == null) {
            return simulationProperties.getInitialBalance();
        }
        if (initialBalance <= 0) {
            throw new InvalidStrategyConfigurationException("Initial balance must be greater than zero");
        }
        return initialBalance;
    }

    private double resolveFeeRate(SimulationAssumptions assumptions) {
        Double feeRate = assumptions.getFeeRate();
        if (feeRate == null) {
            return simulationProperties.getFeeRate();
        }
        if (feeRate < 0 || feeRate >= 1) {
            throw new InvalidStrategyConfigurationException("Fee rate must be between 0 and 1");
        }
        return feeRate;
    }

    private double resolveAllocationAmount(ExecutionModelRequest executionModel, double cashBalance) {
        ExecutionModelType type = executionModel.getType();
        return switch (type) {
            case FULL_BALANCE -> cashBalance;
            case PERCENT_OF_BALANCE -> {
                Double allocationPercent = executionModel.getParams().getAllocationPercent();
                if (allocationPercent == null || allocationPercent <= 0 || allocationPercent > 100) {
                    throw new InvalidStrategyConfigurationException("Allocation percent must be between 1 and 100");
                }
                yield cashBalance * (allocationPercent / 100.0);
            }
            case FIXED_AMOUNT -> {
                Double fixedAmount = executionModel.getParams().getFixedAmount();
                if (fixedAmount == null || fixedAmount <= 0) {
                    throw new InvalidStrategyConfigurationException("Fixed amount must be greater than zero");
                }
                yield Math.min(fixedAmount, cashBalance);
            }
        };
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Builder
    public record BacktestExecution(
            SimulationResult simulationResult,
            TradingRecord record,
            double totalProfit,
            double maximumDrawdown,
            double averageProfit,
            int numberOfTrades
    ) {
    }

    private static final class PortfolioState {
        private double cashBalance;
        private double positionQuantity;
        private double entryNotional;
        private PositionSide positionSide;

        private PortfolioState(double initialBalance) {
            this.cashBalance = initialBalance;
        }
    }
}
