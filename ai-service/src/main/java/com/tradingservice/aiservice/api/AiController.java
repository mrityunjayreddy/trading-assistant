package com.tradingservice.aiservice.api;

import com.tradingservice.aiservice.domain.*;
import com.tradingservice.aiservice.generation.StrategyGenerationService;
import com.tradingservice.aiservice.loop.LearningLoopOrchestrator;
import com.tradingservice.aiservice.memory.StrategyMemoryReader;
import com.tradingservice.aiservice.memory.StrategyMemoryWriter;
import com.tradingservice.aiservice.repository.StrategyMemoryRepository;
import com.tradingservice.aiservice.service.LoopHealthMonitor;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for AI service.
 *
 * Endpoints:
 * POST  /api/ai/memory/store → { strategyId, backtestResultId } → StorageResult
 * POST  /api/ai/generate → GenerationRequest → GenerationResult
 * GET   /api/ai/memory/stats → { totalRecords, avgSharpe, verdictBreakdown, oldestRecord }
 * GET   /api/ai/memory/relevant?symbol=BTCUSDT&interval=1h → top 5 MemoryRecord
 * POST  /api/ai/loop/trigger → 202 Accepted + { loopRunId }
 * GET   /api/ai/loop/status → { lastRunAt, summary, nextRun, isRunning }
 * GET   /api/ai/loop/health → LoopHealth { memoryQuality, successRate, memoryCount, isPaused }
 * GET   /api/ai/loop/history → last 7 audit log summaries
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final StrategyMemoryWriter memoryWriter;
    private final StrategyMemoryReader memoryReader;
    private final StrategyMemoryRepository memoryRepository;
    private final StrategyGenerationService generationService;
    private final LearningLoopOrchestrator loopOrchestrator;
    private final LoopHealthMonitor healthMonitor;

    public AiController(
            StrategyMemoryWriter memoryWriter,
            StrategyMemoryReader memoryReader,
            StrategyMemoryRepository memoryRepository,
            StrategyGenerationService generationService,
            LearningLoopOrchestrator loopOrchestrator,
            LoopHealthMonitor healthMonitor) {
        this.memoryWriter = memoryWriter;
        this.memoryReader = memoryReader;
        this.memoryRepository = memoryRepository;
        this.generationService = generationService;
        this.loopOrchestrator = loopOrchestrator;
        this.healthMonitor = healthMonitor;
    }

    /**
     * POST /api/ai/memory/store
     * Store a backtest result in strategy_memory.
     */
    @PostMapping("/memory/store")
    public ResponseEntity<StorageResult> storeMemory(@RequestBody MemoryStoreRequest request) {
        log.info("Received memory store request for strategy {}", request.strategyId);

        // TODO: Fetch strategy and backtest result from DB
        // For now, return a placeholder response
        StorageResult result = StorageResult.rejected("Not implemented - use learning loop");
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/ai/generate
     * Generate a new strategy using AI.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerationResult> generateStrategy(@Valid @RequestBody GenerationRequest request) {
        log.info("Received strategy generation request for {} {}", request.getSymbol(), request.getInterval());

        GenerationResult result = generationService.generate(request);

        if (result.hasError()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/ai/memory/stats
     * Get memory statistics.
     */
    @GetMapping("/memory/stats")
    public ResponseEntity<Map<String, Object>> getMemoryStats() {
        long totalRecords = memoryRepository.count();
        Double avgSharpe = memoryRepository.avgSharpeSince(Instant.EPOCH);
        if (avgSharpe == null) avgSharpe = 0.0;

        Map<String, Long> verdictBreakdown = memoryRepository.verdictBreakdown().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        Instant oldestRecord = memoryRepository.findFirstByOrderByCreatedAtAsc()
                .map(StrategyMemory::getCreatedAt)
                .orElse(null);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("avgSharpe", avgSharpe);
        stats.put("verdictBreakdown", verdictBreakdown);
        stats.put("oldestRecord", oldestRecord);

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/ai/memory/relevant
     * Get top 5 relevant memories for the given market context.
     */
    @GetMapping("/memory/relevant")
    public ResponseEntity<List<MemoryRecord>> getRelevantMemories(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "5") int topK) {

        MarketContext context = MarketContext.build(symbol, interval);
        List<MemoryRecord> memories = memoryReader.retrieve(context, topK);

        return ResponseEntity.ok(memories);
    }

    /**
     * POST /api/ai/loop/trigger
     * Manually trigger the learning loop.
     */
    @PostMapping("/loop/trigger")
    public ResponseEntity<Map<String, String>> triggerLoop() {
        log.info("Received manual loop trigger request");

        String loopRunId = loopOrchestrator.triggerLoop("api");

        if (loopRunId == null) {
            return ResponseEntity.accepted()
                    .body(Map.of("status", "skipped", "reason", "Loop already running or paused"));
        }

        return ResponseEntity.accepted()
                .body(Map.of("status", "started", "loopRunId", loopRunId));
    }

    /**
     * GET /api/ai/loop/status
     * Get learning loop status.
     */
    @GetMapping("/loop/status")
    public ResponseEntity<Map<String, Object>> getLoopStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("lastRunAt", loopOrchestrator.getLastRunAt());
        status.put("isRunning", loopOrchestrator.isRunning());

        LearningLoopOrchestrator.LoopRunSummary summary = loopOrchestrator.getLastSummary();
        if (summary != null) {
            Map<String, Integer> summaryMap = new HashMap<>();
            summaryMap.put("memoriesStored", summary
                    .getMemoriesStored());
            summaryMap.put("generated", summary.getGeneratedCount());
            summaryMap.put("validated", summary.getValidatedCount());
            summaryMap.put("promoted", summary.getPromotedCount());
            summaryMap.put("pruned", summary.getPrunedCount());
            status.put("lastSummary", summaryMap);
        }

        // Next scheduled run (cron: 0 0 2 * * *)
        status.put("nextRun", "Next scheduled: 02:00 UTC daily");

        return ResponseEntity.ok(status);
    }

    /**
     * GET /api/ai/loop/health
     * Get learning loop health metrics.
     */
    @GetMapping("/loop/health")
    public ResponseEntity<LoopHealth> getLoopHealth() {
        LoopHealth health = healthMonitor.getCurrentHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * GET /api/ai/loop/history
     * Get last 7 audit log summaries.
     */
    @GetMapping("/loop/history")
    public ResponseEntity<List<Map<String, Object>>> getLoopHistory() {
        List<Map<String, Object>> history = healthMonitor.getAuditHistory(7);
        return ResponseEntity.ok(history);
    }

    /**
     * Request DTO for memory store endpoint.
     */
    public record MemoryStoreRequest(UUID strategyId, UUID backtestResultId) {}
}
