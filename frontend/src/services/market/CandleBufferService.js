export class CandleBufferService {
  constructor(limit) {
    this.limit = limit;
  }

  mergeCandles(currentCandles, incomingCandle) {
    const next = currentCandles.filter((candle) => candle.openTime !== incomingCandle.openTime);
    next.push(incomingCandle);
    next.sort((left, right) => left.openTime - right.openTime);
    return next.slice(-this.limit);
  }
}
