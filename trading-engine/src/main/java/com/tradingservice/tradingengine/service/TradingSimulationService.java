package com.tradingservice.tradingengine.service;

import com.tradingservice.tradingengine.dto.SimulationRequest;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.ta4j.StrategyDefinitionResolver;
import com.tradingservice.tradingengine.ta4j.Ta4jMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.BarSeries;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingSimulationService {

    private final HistoricalDataService historicalDataService;
    private final StrategyDefinitionResolver strategyDefinitionResolver;
    private final Ta4jMapper ta4jMapper;
    private final BacktestingService backtestingService;

    public Mono<SimulationResult> simulate(SimulationRequest request) {
        String normalizedSymbol = request.getSymbol().trim().toUpperCase();
        String normalizedInterval = request.getInterval().trim().toLowerCase();
        StrategyDefinition strategyDefinition = strategyDefinitionResolver.resolve(
                request.getStrategy(),
                request.getParams(),
                request.getIndicators(),
                request.getEntryRules(),
                request.getExitRules()
        );
        validateRange(request);

        return historicalDataService.fetchHistoricalKlines(
                        normalizedSymbol,
                        normalizedInterval,
                        request.getRange().getStartTime(),
                        request.getRange().getEndTime(),
                        null
                )
                .map(klines -> executeSimulation(normalizedSymbol, normalizedInterval, strategyDefinition, klines, request))
                .doOnSuccess(result -> log.info("Simulation finished symbol={} interval={} finalBalance={}",
                        normalizedSymbol, normalizedInterval, result.finalBalance()));
    }

    private void validateRange(SimulationRequest request) {
        Long startTime = request.getRange().getStartTime();
        Long endTime = request.getRange().getEndTime();

        if (startTime != null && startTime < 0) {
            throw new InvalidStrategyConfigurationException("Range start time must be positive");
        }

        if (endTime != null && endTime < 0) {
            throw new InvalidStrategyConfigurationException("Range end time must be positive");
        }

        if (startTime != null && endTime != null && startTime >= endTime) {
            throw new InvalidStrategyConfigurationException("Range start time must be before end time");
        }
    }

    private SimulationResult executeSimulation(
            String symbol,
            String interval,
            StrategyDefinition strategyDefinition,
            List<Kline> klines,
            SimulationRequest request
    ) {
        log.info("Running simulation symbol={} interval={} klines={}", symbol, interval, klines.size());
        if (klines.isEmpty()) {
            throw new InvalidStrategyConfigurationException("No kline data returned for the requested simulation range");
        }
        BarSeries series = ta4jMapper.mapToSeries(klines);
        return backtestingService.run(klines, series, strategyDefinition, request.getExecution(), request.getAssumptions());
    }
}
