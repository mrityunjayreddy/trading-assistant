import { createChart, createSeriesMarkers, CandlestickSeries, CrosshairMode } from "lightweight-charts";
import { useEffect, useMemo, useRef, useState } from "react";
import EmptyState from "../shared/EmptyState";
import { formatPercent, formatPrice, formatTime } from "../../utils/formatters";
import "./TradingSimulationChart.css";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

function toChartTime(timestamp) {
  return Math.floor(timestamp / 1000);
}

function formatIntervalLabel(interval) {
  const labels = {
    "1m": "1 Minute",
    "5m": "5 Minutes",
    "15m": "15 Minutes",
    "1h": "1 Hour",
    "4h": "4 Hours",
    "1d": "1 Day",
    "1w": "1 Week"
  };

  return labels[interval] ?? interval?.toUpperCase() ?? "Custom";
}

function formatChartTick(unixSeconds, interval) {
  const date = new Date(unixSeconds * 1000);

  if (interval?.endsWith("m") || interval?.endsWith("h")) {
    return new Intl.DateTimeFormat("en-US", {
      hour: "2-digit",
      minute: "2-digit"
    }).format(date);
  }

  return new Intl.DateTimeFormat("en-US", {
    day: "numeric",
    month: "short",
    year: "2-digit"
  }).format(date);
}

export default function TradingSimulationChart({ interval, result, symbol }) {
  const chartContainerRef = useRef(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);

  const candles = Array.isArray(result?.candles) ? result.candles : [];
  const trades = Array.isArray(result?.trades) ? result.trades : [];
  const totalPages = Math.max(Math.ceil(trades.length / pageSize), 1);
  const pagedTrades = useMemo(() => {
    const start = (page - 1) * pageSize;
    return trades.slice(start, start + pageSize);
  }, [page, pageSize, trades]);

  const chartData = useMemo(() => candles.map((candle) => ({
    time: toChartTime(candle.openTime),
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close
  })), [candles]);

  const markers = useMemo(() => trades.map((trade) => {
    const isBuy = trade.action === "BUY";
    const isShort = trade.positionSide === "SHORT";
    const isOpen = trade.executionType?.startsWith("OPEN");

    return {
      time: toChartTime(trade.timestamp),
      position: isBuy ? "belowBar" : "aboveBar",
      color: isShort ? "#f5b041" : (isBuy ? "#29d391" : "#ff6b7a"),
      shape: isBuy ? "arrowUp" : "arrowDown",
      text: isOpen ? (isShort ? "Open Short" : "Open Long") : (isShort ? "Close Short" : "Close Long")
    };
  }), [trades]);

  useEffect(() => {
    const container = chartContainerRef.current;
    if (!container || !chartData.length) {
      return undefined;
    }

    const chart = createChart(container, {
      autoSize: true,
      height: 420,
      layout: {
        background: { color: "transparent" },
        textColor: "#b8c7db",
        fontFamily: "IBM Plex Mono, monospace"
      },
      grid: {
        vertLines: { color: "rgba(255,255,255,0.06)" },
        horzLines: { color: "rgba(255,255,255,0.06)" }
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: "rgba(120, 210, 255, 0.35)", width: 1 },
        horzLine: { color: "rgba(120, 210, 255, 0.25)", width: 1 }
      },
      leftPriceScale: {
        visible: true,
        borderColor: "rgba(255,255,255,0.08)"
      },
      rightPriceScale: {
        visible: false
      },
      timeScale: {
        borderColor: "rgba(255,255,255,0.08)",
        timeVisible: true,
        secondsVisible: false,
        minimumHeight: 32,
        minBarSpacing: 1.8,
        rightOffset: 6,
        tickMarkFormatter: (time) => formatChartTick(time, interval)
      },
      localization: {
        priceFormatter: (price) => formatPrice(price)
      }
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#29d391",
      downColor: "#ff6b7a",
      borderVisible: false,
      wickUpColor: "#29d391",
      wickDownColor: "#ff6b7a",
      priceLineVisible: true,
      lastValueVisible: true
    });

    candleSeries.setData(chartData);
    createSeriesMarkers(candleSeries, markers);
    chart.timeScale().fitContent();

    return () => {
      chart.remove();
    };
  }, [chartData, markers]);

  useEffect(() => {
    setPage(1);
  }, [pageSize, trades.length]);

  if (!chartData.length) {
    return <EmptyState message="Run a simulation to visualize the trade path." />;
  }

  const peakEquity = Math.max(...(result.equityCurve ?? []).map((point) => point.equity), result.finalBalance);
  const lowEquity = Math.min(...(result.equityCurve ?? []).map((point) => point.equity), result.initialBalance);

  return (
    <div className="simulation-chart">
      <div className="simulation-chart__meta">
        <div>
          <span className="simulation-chart__eyebrow">TradingView Chart</span>
          <h3 className="simulation-chart__title">Candles with trade markers</h3>
          <p className="simulation-chart__subtitle">
            Strategy trades are plotted directly on the Binance candle series used for the backtest.
          </p>
        </div>
        <div className="simulation-chart__stats">
          <div className="simulation-chart__stat">
            <span>Time Frame</span>
            <strong>{formatIntervalLabel(interval)} candles</strong>
          </div>
          <div className="simulation-chart__stat">
            <span>Instrument</span>
            <strong>{symbol ?? "Symbol"}</strong>
          </div>
          <div className="simulation-chart__stat">
            <span>Peak Equity</span>
            <strong>{formatPrice(peakEquity)}</strong>
          </div>
          <div className="simulation-chart__stat">
            <span>Low Equity</span>
            <strong>{formatPrice(lowEquity)}</strong>
          </div>
          <div className="simulation-chart__stat">
            <span>Return</span>
            <strong>{formatPercent(result.totalReturn)}</strong>
          </div>
        </div>
      </div>

        <div className="simulation-chart__surface-shell">
          <div className="simulation-chart__surface" ref={chartContainerRef} />
          <div className="simulation-chart__axis-hint">
            Price scale is pinned to the left. The x-axis uses condensed labels for the selected {formatIntervalLabel(interval).toLowerCase()} interval.
          </div>
        </div>

      {trades.length ? (
        <div className="simulation-chart__table-shell">
          <div className="simulation-chart__table-toolbar">
            <span className="simulation-chart__table-summary">
              Showing {(page - 1) * pageSize + 1}-{Math.min(page * pageSize, trades.length)} of {trades.length} trades
            </span>
            <div className="simulation-chart__pagination">
              <label className="simulation-chart__page-size">
                <span>Rows</span>
                <span className="simulation-chart__page-size-field">
                  <select
                    className="simulation-chart__page-size-select"
                    value={pageSize}
                    onChange={(event) => setPageSize(Number(event.target.value))}
                  >
                    {PAGE_SIZE_OPTIONS.map((option) => (
                      <option key={option} value={option}>
                        {option}
                      </option>
                    ))}
                  </select>
                  <span className="simulation-chart__page-size-caret" aria-hidden="true">
                    ▼
                  </span>
                </span>
              </label>
              <button
                type="button"
                className="simulation-chart__pagination-button"
                disabled={page === 1}
                onClick={() => setPage((current) => Math.max(current - 1, 1))}
              >
                Previous
              </button>
              <span className="simulation-chart__pagination-readout">
                Page {page} / {totalPages}
              </span>
              <button
                type="button"
                className="simulation-chart__pagination-button"
                disabled={page === totalPages}
                onClick={() => setPage((current) => Math.min(current + 1, totalPages))}
              >
                Next
              </button>
            </div>
          </div>
          <div className="simulation-chart__table-scroll">
            <table className="simulation-chart__table">
              <thead>
                <tr>
                  <th>Action</th>
                  <th>Side</th>
                  <th>Time</th>
                  <th>Price</th>
                  <th>Quantity</th>
                  <th>Notional</th>
                  <th>Realized PnL</th>
                  <th>Cash After</th>
                </tr>
              </thead>
              <tbody>
                {pagedTrades.map((trade) => (
                  <tr key={`${trade.timestamp}-${trade.action}-row`}>
                    <td>
                      <span className={`simulation-chart__trade-pill ${trade.action === "BUY" ? "simulation-chart__trade-pill--buy" : "simulation-chart__trade-pill--sell"}`}>
                        {trade.executionType ?? trade.action}
                      </span>
                    </td>
                    <td>{trade.positionSide ?? "-"}</td>
                    <td>{formatTime(trade.timestamp)}</td>
                    <td>{formatPrice(trade.price)}</td>
                    <td>{trade.quantity.toFixed(4)}</td>
                    <td>{formatPrice(trade.notional)}</td>
                    <td>{formatPrice(trade.realizedPnl ?? 0)}</td>
                    <td>{formatPrice(trade.cashBalanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="simulation-chart__empty-ledger">No trades were executed in this simulation window.</div>
      )}
    </div>
  );
}
