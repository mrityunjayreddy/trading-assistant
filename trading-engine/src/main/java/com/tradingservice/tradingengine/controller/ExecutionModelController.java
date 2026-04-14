package com.tradingservice.tradingengine.controller;

import com.tradingservice.tradingengine.dto.ExecutionModelDescriptor;
import com.tradingservice.tradingengine.service.ExecutionModelCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/execution-models", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ExecutionModelController {

    private final ExecutionModelCatalogService executionModelCatalogService;

    @GetMapping
    public List<ExecutionModelDescriptor> listExecutionModels() {
        return executionModelCatalogService.getExecutionModels();
    }
}
