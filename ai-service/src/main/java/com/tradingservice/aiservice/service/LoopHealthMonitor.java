package com.tradingservice.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingservice.aiservice.domain.LoopHealth;
import com.tradingservice.aiservice.loop.LearningLoopOrchestrator;
import com.tradingservice.aiservice.repository.BacktestResultRepository;
import com.tradingservice.aiservice.repository.StrategyMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Monitors and reports learning loop health metrics.
 */
@Component
public class LoopHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(LoopHealthMonitor.class);

    private final BacktestResultRepository backtestResultRepository;
    private final StrategyMemoryRepository memoryRepository;
    private final LearningLoopOrchestrator loopOrchestrator;
    private final ObjectMapper objectMapper;

    public LoopHealthMonitor(
            BacktestResultRepository backtestResultRepository,
            StrategyMemoryRepository memoryRepository,
            LearningLoopOrchestrator loopOrchestrator,
            ObjectMapper objectMapper) {
        this.backtestResultRepository = backtestResultRepository;
        this.memoryRepository = memoryRepository;
        this.loopOrchestrator = loopOrchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * Get current health metrics.
     */
    public LoopHealth getCurrentHealth() {
        // Memory quality: average Sharpe of last 20 stored memories
        Double memoryQuality = backtestResultRepository.avgSharpeOfRecentMemories(20);
        if (memoryQuality == null) memoryQuality = 0.0;

        // Memory count
        long memoryCount = memoryRepository.count();

        // Generation success rate (from last summary)
        LearningLoopOrchestrator.LoopRunSummary summary = loopOrchestrator.getLastSummary();
        double successRate = 0.0;
        if (summary != null && summary.getGeneratedCount() > 0) {
            successRate = (double) summary.getPromotedCount() / summary.getGeneratedCount() * 100.0;
        }

        // Is paused?
        boolean isPaused = loopOrchestrator.isPaused();

        // Consecutive poor runs
        int consecutivePoorRuns = loopOrchestrator.getConsecutivePoorRuns();

        return LoopHealth.builder()
                .memoryQuality(memoryQuality)
                .generationSuccessRate(successRate)
                .memoryCount((int) memoryCount)
                .isPaused(isPaused)
                .consecutivePoorRuns(consecutivePoorRuns)
                .build();
    }

    /**
     * Get last N audit log summaries.
     */
    public List<Map<String, Object>> getAuditHistory(int count) {
        List<Map<String, Object>> history = new ArrayList<>();
        File logsDir = new File("logs");

        if (!logsDir.exists()) {
            return history;
        }

        // Find audit log files
        File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith("learning-loop-") && name.endsWith(".json"));
        if (logFiles == null || logFiles.length == 0) {
            return history;
        }

        // Sort by filename (date) descending
        Arrays.sort(logFiles, (a, b) -> b.getName().compareTo(a.getName()));

        // Read last N files
        for (int i = 0; i < Math.min(count, logFiles.length); i++) {
            try {
                Map<String, Object> auditLog = objectMapper.readValue(
                        logFiles[i],
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );

                // Extract summary only
                Map<String, Object> summary = new HashMap<>();
                summary.put("date", extractDateFromFilename(logFiles[i].getName()));
                summary.put("runAt", auditLog.get("runAt"));
                summary.put("durationMs", auditLog.get("durationMs"));
                summary.put("summary", auditLog.get("summary"));

                history.add(summary);
            } catch (IOException e) {
                log.warn("Failed to read audit log {}: {}", logFiles[i].getName(), e.getMessage());
            }
        }

        return history;
    }

    /**
     * Extract date from filename like "learning-loop-2025-01-15.json".
     */
    private String extractDateFromFilename(String filename) {
        // Extract date between "learning-loop-" and ".json"
        int start = filename.indexOf("learning-loop-") + 14;
        int end = filename.indexOf(".json");
        if (start > 13 && end > start) {
            return filename.substring(start, end);
        }
        return filename;
    }
}
