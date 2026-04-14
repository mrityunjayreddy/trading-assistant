package com.tradingservice.strategyservice.controller;

import com.tradingservice.strategyservice.domain.StrategyDSL;
import com.tradingservice.strategyservice.dto.BacktestSummary;
import com.tradingservice.strategyservice.dto.BatchJob;
import com.tradingservice.strategyservice.dto.RegisterStrategyResponse;
import com.tradingservice.strategyservice.dto.ValidationResult;
import com.tradingservice.strategyservice.entity.StrategyRecord;
import com.tradingservice.strategyservice.exception.ValidationException;
import com.tradingservice.strategyservice.repository.LeaderboardRepository;
import com.tradingservice.strategyservice.service.BatchBacktestOrchestrator;
import com.tradingservice.strategyservice.service.StrategyService;
import com.tradingservice.strategyservice.service.StrategyValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for strategy registry and batch backtest orchestration.
 *
 * <pre>
 * POST   /api/strategies                        — register a new strategy
 * GET    /api/strategies                        — list all active strategies
 * GET    /api/strategies/{id}                   — get strategy by id
 * GET    /api/strategies/leaderboard            — leaderboard (best Sharpe, latest per strategy)
 * DELETE /api/strategies/{id}                   — deactivate a strategy
 * POST   /api/strategies/batch-backtest         — trigger a batch backtest job
 * GET    /api/strategies/batch-backtest/{jobId} — poll a batch job's status
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService           strategyService;
    private final StrategyValidator         strategyValidator;
    private final BatchBacktestOrchestrator batchBacktestOrchestrator;
    private final LeaderboardRepository     leaderboardRepository;

    // ---- POST /api/strategies -----------------------------------------------

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody StrategyDSL dsl) {
        try {
            RegisterStrategyResponse response = strategyService.register(dsl);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(
                    ValidationResult.failed(e.getErrors()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage()));
        }
    }

    // ---- GET /api/strategies ------------------------------------------------

    @GetMapping
    public List<StrategyRecord> listActive() {
        return strategyService.listActive();
    }

    // ---- GET /api/strategies/leaderboard ------------------------------------

    @GetMapping("/leaderboard")
    public List<BacktestSummary> leaderboard(
            @RequestParam(defaultValue = "50") int limit) {
        return leaderboardRepository.fetchLeaderboard(Math.min(limit, 200));
    }

    // ---- GET /api/strategies/{id} -------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(strategyService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---- DELETE /api/strategies/{id} ----------------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        try {
            strategyService.deactivate(id);
            return ResponseEntity.ok(Map.of("status", "DEACTIVATED", "id", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---- POST /api/strategies/batch-backtest --------------------------------

    @PostMapping("/batch-backtest")
    public ResponseEntity<BatchJob> triggerBatchBacktest() {
        BatchJob job = batchBacktestOrchestrator.triggerAsync();
        return ResponseEntity.accepted().body(job);
    }

    // ---- GET /api/strategies/batch-backtest/{jobId} -------------------------

    @GetMapping("/batch-backtest/{jobId}")
    public ResponseEntity<?> getBatchJob(@PathVariable String jobId) {
        BatchJob job = batchBacktestOrchestrator.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}