package com.tradingservice.tradingengine.controller;

import com.tradingservice.tradingengine.dsl.DslSimulationRequest;
import com.tradingservice.tradingengine.dsl.DslStrategyAdapter;
import com.tradingservice.tradingengine.dto.SimulationRequest;
import com.tradingservice.tradingengine.dto.StrategyDefinition;
import com.tradingservice.tradingengine.enricher.StatisticalValidityEnricher;
import com.tradingservice.tradingengine.model.SimulationResult;
import com.tradingservice.tradingengine.persistence.BacktestResultPersister;
import com.tradingservice.tradingengine.service.TradingSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoints for JSON StrategyDSL-based simulation and held-out validation.
 *
 * <ul>
 *   <li>{@code POST /api/v1/simulate/dsl}     — simulate using a StrategyDSL</li>
 *   <li>{@code POST /api/v1/backtest/validate} — identical but restricts range to
 *       the last 20% of the requested window (held-out validation set)</li>
 * </ul>
 *
 * Both endpoints chain through {@link StatisticalValidityEnricher} and
 * {@link BacktestResultPersister} before returning, same as the standard
 * /api/v1/simulate endpoint.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DslSimulationController {

    private final TradingSimulationService tradingSimulationService;
    private final DslStrategyAdapter dslStrategyAdapter;
    private final StatisticalValidityEnricher statisticalValidityEnricher;
    private final BacktestResultPersister backtestResultPersister;

    /**
     * Simulate using a full JSON StrategyDSL.
     * The DSL is converted to the engine's internal StrategyDefinition and
     * injected into the standard simulation pipeline.
     */
    @PostMapping(path = "/simulate/dsl", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SimulationResult> simulateDsl(@Valid @RequestBody DslSimulationRequest request) {
        log.info("DSL simulation request: symbol={} interval={} strategy={}",
                request.getSymbol(), request.getInterval(),
                request.getDsl() != null ? request.getDsl().name() : "null");

        return runSimulation(request);
    }

    /**
     * Validate using held-out data — forces the simulation window to the last 20%
     * of the requested date range.  Useful for out-of-sample strategy validation.
     */
    @PostMapping(path = "/backtest/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SimulationResult> backtestValidate(@Valid @RequestBody DslSimulationRequest request) {
        DslSimulationRequest validationRequest = request.withValidationRange();

        log.info("DSL validation request: symbol={} interval={} validationStart={}",
                validationRequest.getSymbol(), validationRequest.getInterval(),
                validationRequest.getRange().getStartTime());

        return runSimulation(validationRequest);
    }

    // -------------------------------------------------------------------------

    private Mono<SimulationResult> runSimulation(DslSimulationRequest request) {
        // 1. Convert StrategyDSL → StrategyDefinition (existing engine DTO)
        StrategyDefinition strategyDefinition = dslStrategyAdapter.toStrategyDefinition(request.getDsl());

        // 2. Build a standard SimulationRequest with the DSL injected as custom indicators/rules.
        //    StrategyDefinitionResolver.hasCustomDsl() will be true → uses the DSL path, not the name switch.
        SimulationRequest simRequest = SimulationRequest.builder()
                .symbol(request.getSymbol())
                .interval(request.getInterval())
                .strategy("DSL")  // placeholder; overridden because indicators+rules are present
                .indicators(strategyDefinition.getIndicators())
                .entryRules(strategyDefinition.getEntryRules())
                .exitRules(strategyDefinition.getExitRules())
                .execution(request.getExecution())
                .assumptions(request.getAssumptions())
                .range(request.getRange())
                .build();

        // 3. Simulate → enrich → persist
        return tradingSimulationService.simulate(simRequest)
                .flatMap(statisticalValidityEnricher::enrich)
                .flatMap(result -> backtestResultPersister.persist(
                        result,
                        request.getSymbol(),
                        request.getInterval(),
                        request.getRange().getStartTime(),
                        request.getRange().getEndTime()
                ));
    }
}