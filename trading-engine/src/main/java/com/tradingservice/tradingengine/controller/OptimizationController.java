package com.tradingservice.tradingengine.controller;

import com.tradingservice.tradingengine.optimization.OptimizationRequest;
import com.tradingservice.tradingengine.optimization.OptimizationResult;
import com.tradingservice.tradingengine.optimization.OptimizationService;
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
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping(path = "/optimize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<OptimizationResult> optimize(@Valid @RequestBody OptimizationRequest request) {
        log.info("Received optimization request symbol={} interval={} strategy={} metric={}",
                request.getSymbol(), request.getInterval(), request.getStrategy(), request.getMetric());
        return optimizationService.optimize(request);
    }
}
