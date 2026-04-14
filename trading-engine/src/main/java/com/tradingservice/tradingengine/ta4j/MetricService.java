package com.tradingservice.tradingengine.ta4j;

import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetAverageProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;

@Service
public class MetricService {

    private final NetProfitCriterion totalProfitCriterion = new NetProfitCriterion();
    private final MaximumDrawdownCriterion maximumDrawdownCriterion = new MaximumDrawdownCriterion();
    private final NetAverageProfitCriterion averageProfitCriterion = new NetAverageProfitCriterion();
    private final NumberOfPositionsCriterion numberOfTradesCriterion = new NumberOfPositionsCriterion();

    public double totalProfit(BarSeries series, TradingRecord record) {
        return totalProfitCriterion.calculate(series, record).doubleValue();
    }

    public double maximumDrawdown(BarSeries series, TradingRecord record) {
        return maximumDrawdownCriterion.calculate(series, record).doubleValue();
    }

    public double averageProfit(BarSeries series, TradingRecord record) {
        return averageProfitCriterion.calculate(series, record).doubleValue();
    }

    public int numberOfTrades(BarSeries series, TradingRecord record) {
        return numberOfTradesCriterion.calculate(series, record).intValue();
    }
}
