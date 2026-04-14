package com.tradingservice.tradingengine.service;

import com.tradingservice.tradingengine.client.BinanceHistoricalDataClient;
import com.tradingservice.tradingengine.model.Kline;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class HistoricalDataService {

    private final BinanceHistoricalDataClient historicalDataClient;
    private final ConcurrentMap<HistoricalDataCacheKey, Mono<List<Kline>>> cache = new ConcurrentHashMap<>();

    public Mono<List<Kline>> fetchHistoricalKlines(
            String symbol,
            String interval,
            Long startTime,
            Long endTime,
            Integer limit
    ) {
        HistoricalDataCacheKey key = new HistoricalDataCacheKey(symbol, interval, startTime, endTime, limit);
        return cache.computeIfAbsent(key, cacheKey -> historicalDataClient
                .fetchKlines(symbol, interval, startTime, endTime, limit)
                .cache()
                .doOnError(error -> cache.remove(cacheKey)));
    }

    private record HistoricalDataCacheKey(
            String symbol,
            String interval,
            Long startTime,
            Long endTime,
            Integer limit
    ) {
        private HistoricalDataCacheKey {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(interval, "interval");
        }
    }
}
