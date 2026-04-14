package com.tradingservice.strategyservice.exception;

import java.util.List;

/**
 * Thrown by {@link com.tradingservice.strategyservice.service.StrategyValidator}
 * when one or more validation checks fail.
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Strategy validation failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}