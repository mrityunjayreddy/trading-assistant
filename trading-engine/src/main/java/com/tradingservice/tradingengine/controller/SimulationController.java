package com.tradingservice.tradingengine.controller;

import com.tradingservice.tradingengine.dto.SimulationRequest;
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

@Slf4j
@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SimulationController {

    private final TradingSimulationService tradingSimulationService;
    private final StatisticalValidityEnricher statisticalValidityEnricher;
    private final BacktestResultPersister backtestResultPersister;

    @PostMapping(path = "/simulate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SimulationResult> simulate(@Valid @RequestBody SimulationRequest request) {
        log.info("Received simulation request symbol={} interval={} strategy={}",
                request.getSymbol(), request.getInterval(), request.getStrategy());
        Mono<SimulationResult> simulationResultMono = tradingSimulationService.simulate(request)
                .flatMap(statisticalValidityEnricher::enrich)
                .flatMap(result -> backtestResultPersister.persist(result, request));
        return simulationResultMono;
    }
}
