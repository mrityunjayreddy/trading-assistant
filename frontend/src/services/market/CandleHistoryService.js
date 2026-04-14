export class CandleHistoryService {
  async fetchCandles(symbol, limit) {
    const response = await fetch(`/api/candles?symbol=${encodeURIComponent(symbol)}&limit=${limit}`);
    if (!response.ok) {
      throw new Error(`Unable to load candles (${response.status})`);
    }

    const payload = await response.json();
    return Array.isArray(payload) ? payload : [];
  }
}
