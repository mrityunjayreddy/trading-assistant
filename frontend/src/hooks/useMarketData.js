import { useEffect, useRef, useState } from "react";
import { CandleBufferService } from "../services/market/CandleBufferService";
import { CandleHistoryService } from "../services/market/CandleHistoryService";
import { MarketCatalogService } from "../services/market/MarketCatalogService";
import { MarketStreamService } from "../services/market/MarketStreamService";

const CANDLE_LIMIT = 120;
const marketCatalogService = new MarketCatalogService();
const candleHistoryService = new CandleHistoryService();
const marketStreamService = new MarketStreamService();
const candleBufferService = new CandleBufferService(CANDLE_LIMIT);

export function useMarketData() {
  const [symbols, setSymbols] = useState([]);
  const [selectedSymbol, setSelectedSymbol] = useState("BTCUSDT");
  const [candles, setCandles] = useState([]);
  const [connectionState, setConnectionState] = useState("connecting");
  const [lastMessageAt, setLastMessageAt] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const eventSourceRef = useRef(null);

  useEffect(() => {
    let active = true;

    async function loadMarkets() {
      try {
        const nextSymbols = await marketCatalogService.fetchSymbols();
        if (!active) {
          return;
        }

        setSymbols(nextSymbols);
        setSelectedSymbol((currentSymbol) => (
          nextSymbols.length && !nextSymbols.includes(currentSymbol)
            ? nextSymbols[0]
            : currentSymbol
        ));
      } catch (error) {
        if (active) {
          setErrorMessage(error.message);
        }
      }
    }

    loadMarkets();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedSymbol) {
      return undefined;
    }

    let cancelled = false;
    setConnectionState("connecting");
    setErrorMessage("");

    async function loadCandles() {
      try {
        const payload = await candleHistoryService.fetchCandles(selectedSymbol, CANDLE_LIMIT);
        if (!cancelled) {
          setCandles(payload);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error.message);
          setConnectionState("error");
        }
      }
    }

    loadCandles();

    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const eventSource = marketStreamService.subscribe(selectedSymbol, {
      onStatus: () => {
        setConnectionState("live");
      },
      onCandle: (incoming) => {
        setCandles((current) => candleBufferService.mergeCandles(current, incoming));
        setLastMessageAt(Date.now());
        setConnectionState("live");
      },
      onParseError: (error) => {
        setErrorMessage(`Stream parse error: ${error.message}`);
      },
      onError: () => {
        setConnectionState("error");
        setErrorMessage("The live stream disconnected. The browser will keep retrying automatically.");
      }
    });

    eventSourceRef.current = eventSource;

    return () => {
      cancelled = true;
      eventSource.close();
      if (eventSourceRef.current === eventSource) {
        eventSourceRef.current = null;
      }
    };
  }, [selectedSymbol]);

  return {
    candles,
    connectionState,
    errorMessage,
    lastMessageAt,
    selectedSymbol,
    symbols,
    setSelectedSymbol
  };
}
