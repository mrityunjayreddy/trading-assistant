export class MarketStreamService {
  subscribe(symbol, handlers) {
    const eventSource = new EventSource(`/api/market-stream?symbol=${encodeURIComponent(symbol)}`);

    eventSource.addEventListener("status", () => {
      handlers.onStatus?.();
    });

    eventSource.addEventListener("candle", (event) => {
      try {
        handlers.onCandle?.(JSON.parse(event.data));
      } catch (error) {
        handlers.onParseError?.(error);
      }
    });

    eventSource.onerror = () => {
      handlers.onError?.();
    };

    return eventSource;
  }
}
