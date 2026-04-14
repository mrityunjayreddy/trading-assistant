package com.tradingservice.tradingengine.client;

import com.tradingservice.tradingengine.config.BinanceClientProperties;
import com.tradingservice.tradingengine.exception.BinanceClientException;
import com.tradingservice.tradingengine.model.Kline;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceHistoricalDataClient {

    private final WebClient binanceWebClient;
    private final BinanceClientProperties properties;

    public Mono<List<Kline>> fetchKlines(
            String symbol,
            String interval,
            Long startTime,
            Long endTime,
            Integer limit
    ) {
        int normalizedLimit = Optional.ofNullable(limit)
                .orElse(properties.getDefaultLimit());

        log.info("Fetching Binance klines symbol={} interval={} startTime={} endTime={} limit={}",
                symbol, interval, startTime, endTime, normalizedLimit);

        return binanceWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(properties.getKlinesPath())
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("limit", normalizedLimit);
                    if (startTime != null) {
                        uriBuilder.queryParam("startTime", startTime);
                    }
                    if (endTime != null) {
                        uriBuilder.queryParam("endTime", endTime);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("No error payload")
                        .flatMap(body -> Mono.error(new BinanceClientException(
                                "Binance API request failed with status " + response.statusCode().value() + ": " + body
                        ))))
                .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() { })
                .switchIfEmpty(Mono.error(new BinanceClientException("Binance API returned an empty response")))
                .map(this::mapResponse)
                .doOnSuccess(klines -> log.info("Fetched {} klines from Binance for symbol={}", klines.size(), symbol))
                .doOnError(error -> log.error("Failed to fetch Binance klines for symbol={}", symbol, error));
    }

    private List<Kline> mapResponse(List<List<Object>> payload) {
        try {
            return payload.stream()
                    .map(this::mapRow)
                    .toList();
        } catch (RuntimeException exception) {
            throw new BinanceClientException("Failed to parse Binance kline payload", exception);
        }
    }

    private Kline mapRow(List<Object> row) {
        if (row.size() < 6) {
            throw new BinanceClientException("Unexpected Binance kline row size: " + row.size());
        }
        return Kline.builder()
                .openTime(toLong(row.get(0)))
                .open(toDouble(row.get(1)))
                .high(toDouble(row.get(2)))
                .low(toDouble(row.get(3)))
                .close(toDouble(row.get(4)))
                .volume(toDouble(row.get(5)))
                .build();
    }

    private long toLong(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private double toDouble(Object value) {
        return Double.parseDouble(String.valueOf(value));
    }
}
