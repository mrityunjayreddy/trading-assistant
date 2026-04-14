package com.tradingservice.aiservice.loop;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingservice.aiservice.analysis.MarketContextAnalyzer;
import com.tradingservice.aiservice.domain.*;
import com.tradingservice.aiservice.generation.StrategyGenerationService;
import com.tradingservice.aiservice.memory.StrategyMemoryWriter;
import com.tradingservice.aiservice.memory.StrategyMemoryReader;
import com.tradingservice.aiservice.repository.BacktestResultRepository;
import com.tradingservice.aiservice.repository.StrategyRepository;
import com.tradingservice.aiservice.service.TradingEngineClient;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LearningLoopOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LearningLoopOrchestrator.class);

    private final StrategyMemoryWriter memoryWriter;
    private final StrategyMemoryReader memoryReader;
    private final MarketContextAnalyzer contextAnalyzer;
    private final StrategyGenerationService generationService;
    private final BacktestResultRepository backtestResultRepository;
    private final StrategyRepository strategyRepository;
    private final TradingEngineClient tradingEngineClient;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private Instant lastRunAt = null;
    private LoopRunSummary lastSummary = null;
    private int consecutivePoorRuns = 0;
    private boolean isPaused = false;
    private Instant pauseUntil = null;

    public LearningLoopOrchestrator(
            StrategyMemoryWriter memoryWriter,
            StrategyMemoryReader memoryReader,
            MarketContextAnalyzer contextAnalyzer,
            StrategyGenerationService generationService,
            BacktestResultRepository backtestResultRepository,
            StrategyRepository strategyRepository,
            TradingEngineClient tradingEngineClient,
            ObjectMapper objectMapper) {
        this.memoryWriter = memoryWriter;
        this.memoryReader = memoryReader;
        this.contextAnalyzer = contextAnalyzer;
        this.generationService = generationService;
        this.backtestResultRepository = backtestResultRepository;
        this.strategyRepository = strategyRepository;
        this.tradingEngineClient = tradingEngineClient;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public accessors (used by AiController and LoopHealthMonitor)
    // -------------------------------------------------------------------------

    public Instant getLastRunAt() { return lastRunAt; }

    public LoopRunSummary getLastSummary() { return lastSummary; }

    public boolean isRunning() { return isRunning.get(); }

    public boolean isPaused() { return isPaused; }

    public int getConsecutivePoorRuns() { return consecutivePoorRuns; }

    // -------------------------------------------------------------------------

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void runScheduledLoop() {
        triggerLoop("scheduled");
    }

    public String triggerLoop(String triggerSource) {
        if (isPaused) {
            if (pauseUntil != null && Instant.now().isBefore(pauseUntil)) {
                log.warn("Paused until {}. Skipping {}", pauseUntil, triggerSource);
                return null;
            } else {
                isPaused = false;
            }
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Already running. Skipping {}", triggerSource);
            return null;
        }

        String loopRunId = UUID.randomUUID().toString();

        CompletableFuture.runAsync(() -> {
            try {
                executeLoop(loopRunId);
            } catch (Exception e) {
                log.error("Loop failed", e);
            } finally {
                isRunning.set(false);
            }
        });

        return loopRunId;
    }

    private void executeLoop(String loopRunId) throws Exception {
        long start = System.currentTimeMillis();

        LoopRunSummary summary = new LoopRunSummary();

        StepRecord step1 = executeStep1();
        List<BacktestResultRecord> results = (List<BacktestResultRecord>) step1.data;
        summary.setFetchedCount(step1.count);

        StepRecord step2 = executeStep2(results);
        summary.setMemoriesStored(step2.count);

        StepRecord step3 = executeStep3(results);
        summary.setAnalysis((LoopAnalysis) step3.data);

        StepRecord step4 = executeStep4(summary.getAnalysis());
        summary.setGeneratedCount(step4.count);

        StepRecord step5 = executeStep5((List<GenerationResult>) step4.data);
        summary.setValidatedCount(step5.count);

        StepRecord step6 = executeStep6((List<ValidatedStrategy>) step5.data);
        summary.setBacktestPassedCount(step6.count);

        StepRecord step7 = executeStep7((List<PromotableStrategy>) step6.data);
        summary.setPromotedCount(step7.count);

        StepRecord step8 = executeStep8();
        summary.setPrunedCount(step8.count);

        lastRunAt = Instant.now();
        lastSummary = summary;

        log.info("Loop done in {} ms", System.currentTimeMillis() - start);
    }

    private StepRecord executeStep1() {
        List<BacktestResultRecord> results = backtestResultRepository.findAllByOrderByCreatedAtDesc();
        return new StepRecord(1, "FETCH", results.size(), 0, results);
    }

    private StepRecord executeStep2(List<BacktestResultRecord> results) {
        int stored = 0;

        for (BacktestResultRecord r : results) {
            StrategyRecord strategy = strategyRepository.findById(r.getStrategyId()).orElse(null);
            if (strategy == null) continue;

            MarketContext ctx = contextAnalyzer.analyze(r.getSymbol(), r.getInterval());
            StorageResult res = memoryWriter.store(strategy, r, ctx);

            if (res.isStored()) stored++;
        }

        return new StepRecord(2, "STORE", stored, 0, null);
    }

    private StepRecord executeStep3(List<BacktestResultRecord> results) {
        LoopAnalysis analysis = new LoopAnalysis();

        double avg = results.stream()
                .filter(r -> r.getSharpeRatio() != null)
                .mapToDouble(BacktestResultRecord::getSharpeRatio)
                .average().orElse(0);

        analysis.setAvgSharpe(avg);
        analysis.setCurrentContext(contextAnalyzer.analyze("BTCUSDT", "1h"));

        return new StepRecord(3, "ANALYZE", 0, 0, analysis);
    }

    private StepRecord executeStep4(LoopAnalysis analysis) {
        List<GenerationResult> list = new ArrayList<>();
        list.add(generationService.generate(
                GenerationRequest.builder()
                        .symbol("BTCUSDT")
                        .interval("1h")
                        .objective("Improve Sharpe")
                        .build()
        ));
        return new StepRecord(4, "GENERATE", list.size(), 0, list);
    }

    private StepRecord executeStep5(List<GenerationResult> gens) {
        List<ValidatedStrategy> out = new ArrayList<>();

        for (GenerationResult g : gens) {
            if (!g.hasError()) {
                out.add(new ValidatedStrategy(g.getDsl(), g));
            }
        }
        return new StepRecord(5, "VALIDATE", out.size(), 0, out);
    }

    private StepRecord executeStep6(List<ValidatedStrategy> list) {
        List<PromotableStrategy> out = new ArrayList<>();

        for (ValidatedStrategy v : list) {
            BacktestResultRecord r = tradingEngineClient.backtest(v.dsl);
            if (r != null && r.getSharpeRatio() != null && r.getSharpeRatio() > 0.3) {
                out.add(new PromotableStrategy(v.dsl, v.generation, r));
            }
        }
        return new StepRecord(6, "BACKTEST", out.size(), 0, out);
    }

    private StepRecord executeStep7(List<PromotableStrategy> list) {
        int count = 0;
        for (PromotableStrategy p : list) {
            if (strategyRepository.saveStrategy(p.dsl)) count++;
        }
        return new StepRecord(7, "PROMOTE", count, 0, null);
    }

    private StepRecord executeStep8() {
        return new StepRecord(8, "PRUNE", 0, 0, null);
    }

    private String extractName(String dsl) {
        try {
            JsonNode node = objectMapper.readTree(dsl);
            return node.path("name").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private record StepRecord(int stepNumber, String action, int count, long durationMs, Object data) {}

    @Getter
    @Setter
    public static class LoopRunSummary {
        private int fetchedCount;
        private int memoriesStored;
        private LoopAnalysis analysis;
        private int generatedCount;
        private int validatedCount;
        private int backtestPassedCount;
        private int promotedCount;
        private int prunedCount;
    }

    @Getter
    @Setter
    public static class LoopAnalysis {
        private UUID bestStrategyId;
        private Double bestSharpe;
        private UUID worstStrategyId;
        private Double worstSharpe;
        private Double avgSharpe;
        private String dominantExitReason;
        private MarketContext currentContext;
    }

    private record ValidatedStrategy(String dsl, GenerationResult generation) {}
    private record PromotableStrategy(String dsl, GenerationResult generation, BacktestResultRecord backtestResult) {}
}

