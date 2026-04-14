import { useMemo } from "react";

export function useMarketStats(candles) {
  return useMemo(() => {
    const latest = candles[candles.length - 1];
    const first = candles[0];

    if (!latest || !first) {
      return {
        lastPrice: 0,
        percentMove: 0,
        rangeHigh: 0,
        rangeLow: 0,
        volume: 0
      };
    }

    return {
      lastPrice: latest.close,
      percentMove: ((latest.close - first.open) / first.open) * 100,
      rangeHigh: Math.max(...candles.map((candle) => candle.high)),
      rangeLow: Math.min(...candles.map((candle) => candle.low)),
      volume: candles.reduce((sum, candle) => sum + candle.volume, 0)
    };
  }, [candles]);
}
