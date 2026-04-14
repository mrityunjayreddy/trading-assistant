package com.tradingservice.tradingengine.ta4j;

import com.tradingservice.tradingengine.model.Kline;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseBarSeries;

@Component
public class Ta4jMapper {

    private static final Duration DEFAULT_BAR_DURATION = Duration.ofMinutes(1);

    public BarSeries mapToSeries(List<Kline> klines) {
        BaseBarSeries series = new BaseBarSeriesBuilder()
                .withName("market-data")
                .build();
        if (klines.isEmpty()) {
            return series;
        }

        Duration barDuration = resolveBarDuration(klines);
        for (Kline kline : klines) {
            Instant endTime = Instant.ofEpochMilli(kline.openTime());
            series.addBar(new BaseBar(
                    barDuration,
                    endTime.minus(barDuration),
                    endTime,
                    series.numFactory().numOf(kline.open()),
                    series.numFactory().numOf(kline.high()),
                    series.numFactory().numOf(kline.low()),
                    series.numFactory().numOf(kline.close()),
                    series.numFactory().numOf(kline.volume()),
                    series.numFactory().numOf(kline.close() * kline.volume()),
                    0L
            ));
        }
        return series;
    }

    private Duration resolveBarDuration(List<Kline> klines) {
        if (klines.size() < 2) {
            return DEFAULT_BAR_DURATION;
        }
        long deltaMillis = Math.max(1L, klines.get(1).openTime() - klines.get(0).openTime());
        return Duration.ofMillis(deltaMillis);
    }
}
