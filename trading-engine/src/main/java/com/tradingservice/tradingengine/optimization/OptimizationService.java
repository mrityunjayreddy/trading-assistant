package com.tradingservice.tradingengine.optimization;

import com.tradingservice.tradingengine.config.OptimizationProperties;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import com.tradingservice.tradingengine.model.Kline;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.service.BacktestingService;
import com.tradingservice.tradingengine.service.HistoricalDataService;
import com.tradingservice.tradingengine.ta4j.StrategyDefinitionResolver;
import com.tradingservice.tradingengine.ta4j.Ta4jMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationService {

    private final HistoricalDataService historicalDataService;
    private final StrategyDefinitionResolver strategyDefinitionResolver;
    private final Ta4jMapper ta4jMapper;
    private final BacktestingService backtestingService;
    private final GridGenerator gridGenerator;
    private final MetricEvaluator metricEvaluator;
    private final ExecutorService optimizationExecutorService;
    private final OptimizationProperties optimizationProperties;

    public Mono<OptimizationResult> optimize(OptimizationRequest request) {
        validateRange(request);
        String normalizedSymbol = request.getSymbol().trim().toUpperCase();
        String normalizedInterval = request.getInterval().trim().toLowerCase();
        List<Map<String, Object>> combinations = gridGenerator.generate(request.getParamGrid());

        return historicalDataService.fetchHistoricalKlines(
                        normalizedSymbol,
                        normalizedInterval,
                        request.getRange().getStartTime(),
                        request.getRange().getEndTime(),
                        null
                )
                .flatMap(klines -> Mono.fromFuture(runOptimization(normalizedSymbol, normalizedInterval, klines, request, combinations)));
    }

    private CompletableFuture<OptimizationResult> runOptimization(
            String symbol,
            String interval,
            List<Kline> klines,
            OptimizationRequest request,
            List<Map<String, Object>> combinations
    ) {
        if (klines.isEmpty()) {
            throw new InvalidStrategyConfigurationException("No kline data returned for the requested optimization range");
        }
        BarSeries series = ta4jMapper.mapToSeries(klines);

        log.info("Starting optimization symbol={} interval={} strategy={} combinations={} metric={}",
                symbol, interval, request.getStrategy(), combinations.size(), request.getMetric());

        List<CompletableFuture<Optional<OptimizationCandidate>>> futures = combinations.stream()
                .map(params -> CompletableFuture
                        .supplyAsync(() -> executeCombination(request, klines, series, params), optimizationExecutorService)
                        .completeOnTimeout(Optional.empty(), optimizationProperties.getTaskTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .exceptionally(throwable -> {
                            log.warn("Optimization combination failed strategy={} params={} reason={}",
                                    request.getStrategy(), params, rootCauseMessage(throwable));
                            return Optional.empty();
                        }))
                .toList();

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return all.thenApply(unused -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .sorted(Comparator.comparingDouble(OptimizationCandidate::score).reversed())
                        .toList())
                .thenApply(candidates -> toResult(request, combinations.size(), candidates));
    }

    private Optional<OptimizationCandidate> executeCombination(
            OptimizationRequest request,
            List<Kline> klines,
            BarSeries series,
            Map<String, Object> params
    ) {
        try {
            log.info("Executing optimization strategy={} params={}", request.getStrategy(), params);
            StrategyDefinition strategyDefinition = strategyDefinitionResolver.resolve(
                    request.getStrategy(),
                    params,
                    request.getIndicators(),
                    request.getEntryRules(),
                    request.getExitRules()
            );
            BacktestingService.BacktestExecution execution = backtestingService.runDetailed(
                    klines,
                    series,
                    strategyDefinition,
                    request.getExecution(),
                    request.getAssumptions()
            );
            SimulationResult result = execution.simulationResult();
            SimulationResultSummary summary = summarize(params, execution, result, request.getMetric());
            return Optional.of(new OptimizationCandidate(summary.score(), summary));
        } catch (InvalidStrategyConfigurationException exception) {
            log.warn("Skipping invalid optimization params strategy={} params={} reason={}",
                    request.getStrategy(), params, exception.getMessage());
            return Optional.empty();
        }
    }

    private SimulationResultSummary summarize(
            Map<String, Object> params,
            BacktestingService.BacktestExecution execution,
            SimulationResult result,
            MetricType metric
    ) {
        double sharpeRatio = round(metricEvaluator.calculateSharpeRatio(result));
        double maxDrawdown = round(execution.maximumDrawdown());
        double winRate = round(metricEvaluator.calculateWinRate(result));
        double score = metric == MetricType.MAX_DRAWDOWN
                ? round(-execution.maximumDrawdown())
                : round(metricEvaluator.evaluate(result, metric));

        return SimulationResultSummary.builder()
                .params(params)
                .totalReturn(round(result.totalReturn()))
                .maxDrawdown(maxDrawdown)
                .tradesCount(execution.numberOfTrades())
                .winRate(winRate)
                .sharpeRatio(sharpeRatio)
                .score(score)
                .build();
    }

    private OptimizationResult toResult(
            OptimizationRequest request,
            int evaluatedCombinations,
            List<OptimizationCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            throw new InvalidStrategyConfigurationException("No valid optimization results were produced for the supplied parameter grid");
        }

        List<SimulationResultSummary> topResults = candidates.stream()
                .limit(optimizationProperties.getTopResultsLimit())
                .map(OptimizationCandidate::summary)
                .toList();

        SimulationResultSummary best = topResults.get(0);
        log.info("Optimization complete strategy={} metric={} bestScore={} bestParams={}",
                request.getStrategy(), request.getMetric(), best.score(), best.params());

        return OptimizationResult.builder()
                .bestParams(best.params())
                .bestScore(best.score())
                .metricUsed(request.getMetric())
                .evaluatedCombinations(evaluatedCombinations)
                .successfulCombinations(candidates.size())
                .topResults(topResults)
                .build();
    }

    private void validateRange(OptimizationRequest request) {
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

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Builder
    private record OptimizationCandidate(
            double score,
            SimulationResultSummary summary
    ) {
    }
}
