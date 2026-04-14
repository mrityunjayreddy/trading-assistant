package com.tradingservice.tradingengine.exception;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.PayloadTooLargeException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            WebExchangeBindException exception,
            ServerWebExchange exchange
    ) {
        String message = exception.getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, message, exchange.getRequest().getPath().value(), exception);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleWebInputException(
            ServerWebInputException exception,
            ServerWebExchange exchange
    ) {
        String message = exception.getReason() == null ? "Malformed request" : exception.getReason();
        return buildResponse(HttpStatus.BAD_REQUEST, message, exchange.getRequest().getPath().value(), exception);
    }

    @ExceptionHandler(InvalidStrategyConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStrategyConfiguration(
            InvalidStrategyConfigurationException exception,
            ServerWebExchange exchange
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), exchange.getRequest().getPath().value(), exception);
    }

    @ExceptionHandler(BinanceClientException.class)
    public ResponseEntity<ApiErrorResponse> handleBinanceClientException(
            BinanceClientException exception,
            ServerWebExchange exchange
    ) {
        return buildResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), exchange.getRequest().getPath().value(), exception);
    }

    @ExceptionHandler({PayloadTooLargeException.class, DataBufferLimitException.class})
    public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(
            Exception exception,
            ServerWebExchange exchange
    ) {
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Request payload is too large. Reduce optimization grid size or increase the configured request limit.",
                exchange.getRequest().getPath().value(),
                exception
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            ServerWebExchange exchange
    ) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error",
                exchange.getRequest().getPath().value(),
                exception);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            String path,
            Exception exception
    ) {
        log.error("Request failed status={} path={} message={}", status.value(), path, message, exception);
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
