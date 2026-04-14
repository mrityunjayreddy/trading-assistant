import {
  AreaSeries,
  CandlestickSeries,
  CrosshairMode,
  LineSeries,
  createChart,
  createSeriesMarkers
} from "lightweight-charts";
import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import EmptyState from "../../shared/EmptyState";
import { formatPercent, formatPrice, formatTime } from "../../../utils/formatters";
import { computeIndicatorSeries, INDICATOR_LIBRARY } from "../../../utils/strategyDsl";

function toChartTime(timestamp) {
  return Math.floor(timestamp / 1000);
}

function intervalToSeconds(interval) {
  const match = String(interval ?? "1h").match(/^(\d+)([mhdw])$/);
  if (!match) return 3600;
  const units = { m: 60, h: 3600, d: 86400, w: 604800 };
  return parseInt(match[1], 10) * (units[match[2]] ?? 3600);
}

function buildTradePairs(trades) {
  const pairs = [];
  let openTrade = null;
  for (const trade of trades) {
    const isClose = String(trade.executionType ?? "").startsWith("CLOSE");
    if (!isClose && openTrade == null) {
      openTrade = trade;
    } else if (isClose && openTrade != null) {
      pairs.push({ entry: openTrade, exit: trade, pnl: trade.realizedPnl ?? 0 });
      openTrade = null;
    }
  }
  return pairs;
}

function redrawTradeShading(canvas, chart, tradePairs) {
  if (!canvas || !chart) return;
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  for (const { entry, exit, pnl } of tradePairs) {
    const x1 = chart.timeScale().timeToCoordinate(toChartTime(entry.timestamp));
    const x2 = chart.timeScale().timeToCoordinate(toChartTime(exit.timestamp));
    if (x1 == null || x2 == null) continue;
    const left = Math.min(x1, x2);
    const w = Math.max(Math.abs(x2 - x1), 2);
    ctx.fillStyle = pnl >= 0 ? "rgba(41,211,145,0.09)" : "rgba(255,107,122,0.09)";
    ctx.fillRect(left, 0, w, canvas.height);
  }
}

function TradeTooltip({ tooltip }) {
  return (
    <div
      className="chart-trade-tooltip"
      style={{ top: tooltip.y, left: tooltip.x }}
    >
      {tooltip.trades.map((trade, i) => {
        const isClose = String(trade.executionType ?? "").startsWith("CLOSE");
        const pnl = trade.realizedPnl ?? 0;
        return (
          <div key={`${trade.timestamp}-${i}`}>
            {i > 0 && <div className="chart-trade-tooltip__divider" />}
            <div className="chart-trade-tooltip__heading">{trade.executionType ?? trade.action}</div>
            <div className="chart-trade-tooltip__row">
              <span>Time</span>
              <span>{formatTime(trade.timestamp)}</span>
            </div>
            <div className="chart-trade-tooltip__row">
              <span>Price</span>
              <span>{formatPrice(trade.price)}</span>
            </div>
            {trade.positionSide && (
              <div className="chart-trade-tooltip__row">
                <span>Side</span>
                <span>{trade.positionSide}</span>
              </div>
            )}
            {isClose && (
              <div className="chart-trade-tooltip__row">
                <span>PnL</span>
                <span className={pnl >= 0 ? "chart-trade-tooltip__pnl--pos" : "chart-trade-tooltip__pnl--neg"}>
                  {formatPrice(pnl)}
                </span>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function ChartLegend({ items }) {
  const entries = Object.entries(items);
  if (!entries.length) return null;
  return (
    <div className="chart-legend">
      {entries.map(([key, { label, color, value }]) => (
        <div className="chart-legend__item" key={key}>
          <span className="chart-legend__swatch" style={{ background: color }} />
          <span>{label}: {typeof value === "number" ? value.toFixed(2) : "—"}</span>
        </div>
      ))}
    </div>
  );
}

const CHART_LAYOUT = {
  background: { color: "transparent" },
  textColor: "#b8c7db",
  fontFamily: "IBM Plex Mono, monospace"
};

const BT_CHART_LAYOUT = {
  background: { color: "#0b0e11" },
  textColor: "#848e9c",
  fontFamily: "SF Pro Text, system-ui, sans-serif"
};

const OSC_COLORS = ["#78d2ff", "#f7c75f", "#29d391", "#ff6b7a", "#c87cf7", "#ff9b54"];

const StrategyMarketChart = forwardRef(function StrategyMarketChart({ dslState, interval, result, symbol, btMode }, ref) {
  const marketChartRef = useRef(null);
  const equityChartRef = useRef(null);
  const oscillatorChartRef = useRef(null);
  const shadingCanvasRef = useRef(null);
  const chartInstanceRef = useRef(null);
  const containerDimsRef = useRef({ width: 0, height: 0 });
  const intervalRef = useRef(interval);

  const [tooltip, setTooltip] = useState(null);
  const [legendValues, setLegendValues] = useState({});

  useEffect(() => { intervalRef.current = interval; }, [interval]);

  const candles = Array.isArray(result?.candles) ? result.candles : [];
  const trades = Array.isArray(result?.trades) ? result.trades : [];
  const equityCurve = Array.isArray(result?.equityCurve) ? result.equityCurve : [];

  const overlays = useMemo(() => computeIndicatorSeries(dslState.indicators, candles), [candles, dslState.indicators]);

  const overlayIndicators = useMemo(
    () => dslState.indicators.filter((ind) => INDICATOR_LIBRARY[ind.type]?.chartOverlay),
    [dslState.indicators]
  );

  const oscillatorIndicators = useMemo(
    () => dslState.indicators.filter((ind) => ind.type in INDICATOR_LIBRARY && !INDICATOR_LIBRARY[ind.type]?.chartOverlay),
    [dslState.indicators]
  );

  const marketData = useMemo(() => candles.map((c) => ({
    time: toChartTime(c.openTime),
    open: c.open,
    high: c.high,
    low: c.low,
    close: c.close
  })), [candles]);

  const equityData = useMemo(() => equityCurve.map((p) => ({
    time: toChartTime(p.timestamp),
    value: p.equity
  })), [equityCurve]);

  const markers = useMemo(() => trades.map((trade) => ({
    time: toChartTime(trade.timestamp),
    position: trade.action === "BUY" ? "belowBar" : "aboveBar",
    color: trade.action === "BUY" ? "#29d391" : "#ff6b7a",
    shape: trade.action === "BUY" ? "arrowUp" : "arrowDown",
    text: trade.executionType ?? trade.action
  })), [trades]);

  const tradePairs = useMemo(() => buildTradePairs(trades), [trades]);

  const tradesByTime = useMemo(() => {
    const map = new Map();
    trades.forEach((trade) => {
      const t = toChartTime(trade.timestamp);
      if (!map.has(t)) map.set(t, []);
      map.get(t).push(trade);
    });
    return map;
  }, [trades]);

  // Expose zoom-to-trade imperatively via ref
  useImperativeHandle(ref, () => ({
    scrollToTime(timestamp) {
      const chart = chartInstanceRef.current;
      if (!chart) return;
      const t = toChartTime(timestamp);
      const secs = intervalToSeconds(intervalRef.current);
      chart.timeScale().setVisibleRange({ from: t - 50 * secs, to: t + 50 * secs });
    }
  }), []);

  // ── Main market chart ──────────────────────────────────────────────────────
  useEffect(() => {
    const container = marketChartRef.current;
    const canvas = shadingCanvasRef.current;
    if (!container || !marketData.length) {
      chartInstanceRef.current = null;
      return undefined;
    }

    containerDimsRef.current = { width: container.clientWidth, height: container.clientHeight };

    function syncCanvasSize() {
      if (!canvas) return;
      canvas.width = container.clientWidth;
      canvas.height = container.clientHeight;
    }
    syncCanvasSize();

    const ro = new ResizeObserver(() => {
      containerDimsRef.current = { width: container.clientWidth, height: container.clientHeight };
      syncCanvasSize();
      redrawTradeShading(canvas, chart, tradePairs);
    });
    ro.observe(container);

    const btGrid = { color: "#2b3139" };
    const stdGrid = { color: "rgba(255,255,255,0.06)" };
    const chart = createChart(container, {
      autoSize: true,
      height: 500,
      layout: btMode ? BT_CHART_LAYOUT : CHART_LAYOUT,
      grid: {
        vertLines: btMode ? btGrid : stdGrid,
        horzLines: btMode ? btGrid : stdGrid
      },
      crosshair: { mode: CrosshairMode.Normal },
      leftPriceScale: { visible: true, borderColor: btMode ? "#2b3139" : "rgba(255,255,255,0.08)" },
      rightPriceScale: { visible: false },
      timeScale: {
        borderColor: btMode ? "#2b3139" : "rgba(255,255,255,0.08)",
        timeVisible: true,
        secondsVisible: false
      },
      localization: { priceFormatter: (p) => formatPrice(p) }
    });
    chartInstanceRef.current = chart;

    const upColor = btMode ? "#0ecb81" : "#29d391";
    const downColor = btMode ? "#f6465d" : "#ff6b7a";
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor,
      downColor,
      borderVisible: false,
      wickUpColor: upColor,
      wickDownColor: downColor
    });
    candleSeries.setData(marketData);
    createSeriesMarkers(candleSeries, markers);

    // Overlay indicator series — build registry for legend lookup
    const seriesRegistry = new Map(); // key → { series, label, color }
    overlayIndicators.forEach((indicator) => {
      const definition = INDICATOR_LIBRARY[indicator.type];
      const keys = definition?.chartSeries?.length
        ? definition.chartSeries.map((sd) => ({
          key: `${indicator.id}${sd.key}`,
          isBandLine: Boolean(sd.band),
          label: sd.label
        }))
        : [{ key: indicator.id, isBandLine: false, label: definition?.shortLabel ?? indicator.id }];

      keys.forEach(({ key, isBandLine, label }) => {
        const color = isBandLine
          ? (indicator.style?.bandColor ?? indicator.style?.color ?? "#f7c75f")
          : (indicator.style?.color ?? "#78d2ff");
        const s = chart.addSeries(LineSeries, {
          color,
          lineWidth: isBandLine
            ? Math.max(1, Number(indicator.style?.lineWidth ?? 2) - 1)
            : Number(indicator.style?.lineWidth ?? 2),
          priceLineVisible: false,
          lastValueVisible: false
        });
        s.setData(
          (overlays[key] ?? [])
            .map((value, i) => ({ time: marketData[i]?.time, value }))
            .filter((p) => p.time != null && Number.isFinite(p.value))
        );
        seriesRegistry.set(key, { series: s, label, color });
      });
    });

    // Initial visible range
    const visibleCount = 150;
    if (marketData.length > visibleCount) {
      chart.timeScale().setVisibleLogicalRange({ from: marketData.length - visibleCount, to: marketData.length - 1 });
    } else {
      chart.timeScale().fitContent();
    }

    // Trade shading — draw after chart renders, redraw on pan/zoom
    const shadingTimer = setTimeout(() => redrawTradeShading(canvas, chart, tradePairs), 60);
    chart.timeScale().subscribeVisibleLogicalRangeChange(() => {
      redrawTradeShading(canvas, chart, tradePairs);
    });

    // Crosshair move — update legend and trade tooltip
    chart.subscribeCrosshairMove((param) => {
      if (!param.point || !param.time) {
        setTooltip(null);
        setLegendValues({});
        return;
      }

      // Build legend values from series data at crosshair
      const nextLegend = {};
      seriesRegistry.forEach(({ series, label, color }, key) => {
        const data = param.seriesData.get(series);
        if (data?.value != null) {
          nextLegend[key] = { label, color, value: data.value };
        }
      });
      setLegendValues(nextLegend);

      // Show trade tooltip when crosshair is on a trade bar
      const tradesHere = tradesByTime.get(param.time);
      if (tradesHere?.length) {
        const { width: cw, height: ch } = containerDimsRef.current;
        const TOOLTIP_W = 200;
        const TOOLTIP_H = 110;
        const rawX = param.point.x + 16;
        const x = rawX + TOOLTIP_W > cw ? param.point.x - 16 - TOOLTIP_W : rawX;
        const y = Math.min(Math.max(param.point.y - TOOLTIP_H / 2, 8), ch - TOOLTIP_H - 8);
        setTooltip({ x, y, trades: tradesHere });
      } else {
        setTooltip(null);
      }
    });

    return () => {
      clearTimeout(shadingTimer);
      ro.disconnect();
      chartInstanceRef.current = null;
      chart.remove();
      setTooltip(null);
      setLegendValues({});
    };
  }, [marketData, markers, overlayIndicators, overlays, tradePairs, tradesByTime]);

  // ── Oscillator sub-pane ────────────────────────────────────────────────────
  useEffect(() => {
    const container = oscillatorChartRef.current;
    if (!container || !marketData.length || !oscillatorIndicators.length) {
      return undefined;
    }

    const mainChart = chartInstanceRef.current;

    const oscGrid = btMode ? { color: "#2b3139" } : { color: "rgba(255,255,255,0.04)" };
    const chart = createChart(container, {
      autoSize: true,
      height: 160,
      layout: btMode ? BT_CHART_LAYOUT : CHART_LAYOUT,
      grid: { vertLines: oscGrid, horzLines: oscGrid },
      crosshair: { mode: CrosshairMode.Normal },
      leftPriceScale: { visible: true, borderColor: btMode ? "#2b3139" : "rgba(255,255,255,0.08)" },
      rightPriceScale: { visible: false },
      timeScale: {
        borderColor: btMode ? "#2b3139" : "rgba(255,255,255,0.08)",
        timeVisible: true,
        secondsVisible: false
      }
    });

    oscillatorIndicators.forEach((indicator, i) => {
      const definition = INDICATOR_LIBRARY[indicator.type];
      const color = indicator.style?.color ?? OSC_COLORS[i % OSC_COLORS.length];
      const s = chart.addSeries(LineSeries, {
        color,
        lineWidth: Number(indicator.style?.lineWidth ?? 2),
        priceLineVisible: false,
        lastValueVisible: false,
        title: definition?.shortLabel ?? indicator.id
      });
      s.setData(
        (overlays[indicator.id] ?? [])
          .map((value, idx) => ({ time: marketData[idx]?.time, value }))
          .filter((p) => p.time != null && Number.isFinite(p.value))
      );
    });

    chart.timeScale().fitContent();

    // Bidirectional time scale sync with main chart
    let syncing = false;
    function onMainRangeChange(range) {
      if (syncing || !range) return;
      syncing = true;
      chart.timeScale().setVisibleLogicalRange(range);
      syncing = false;
    }
    function onOscRangeChange(range) {
      if (syncing || !range || !mainChart) return;
      syncing = true;
      mainChart.timeScale().setVisibleLogicalRange(range);
      syncing = false;
    }

    if (mainChart) {
      mainChart.timeScale().subscribeVisibleLogicalRangeChange(onMainRangeChange);
      const currentRange = mainChart.timeScale().getVisibleLogicalRange();
      if (currentRange) chart.timeScale().setVisibleLogicalRange(currentRange);
    }
    chart.timeScale().subscribeVisibleLogicalRangeChange(onOscRangeChange);

    return () => {
      if (mainChart) {
        mainChart.timeScale().unsubscribeVisibleLogicalRangeChange(onMainRangeChange);
      }
      chart.remove();
    };
  }, [marketData, oscillatorIndicators, overlays]);

  // ── Equity curve chart ─────────────────────────────────────────────────────
  useEffect(() => {
    const container = equityChartRef.current;
    if (!container || !equityData.length) return undefined;

    const chart = createChart(container, {
      autoSize: true,
      height: 240,
      layout: CHART_LAYOUT,
      grid: {
        vertLines: { color: "rgba(255,255,255,0.05)" },
        horzLines: { color: "rgba(255,255,255,0.05)" }
      },
      leftPriceScale: { visible: true, borderColor: "rgba(255,255,255,0.08)" },
      rightPriceScale: { visible: false },
      timeScale: { borderColor: "rgba(255,255,255,0.08)", timeVisible: true },
      localization: { priceFormatter: (p) => formatPrice(p) }
    });

    const series = chart.addSeries(AreaSeries, {
      topColor: "rgba(120,210,255,0.26)",
      bottomColor: "rgba(120,210,255,0.02)",
      lineColor: "#78d2ff",
      lineWidth: 2
    });
    series.setData(equityData);
    chart.timeScale().fitContent();
    return () => chart.remove();
  }, [equityData]);

  if (!marketData.length) {
    if (btMode) {
      return <div className="bt-empty">Run a simulation to load candle data.</div>;
    }
    return <EmptyState message="Run a simulation to see the market structure, indicators, and equity curve." />;
  }

  if (btMode) {
    return (
      <div style={{ display: "flex", flexDirection: "column", height: "100%", overflow: "hidden" }}>
        <div style={{ position: "relative", flex: 1, minHeight: 0 }}>
          <div ref={marketChartRef} style={{ width: "100%", height: "100%" }} />
          <canvas
            ref={shadingCanvasRef}
            style={{ position: "absolute", inset: 0, pointerEvents: "none" }}
          />
          <ChartLegend items={legendValues} />
          {tooltip && <TradeTooltip tooltip={tooltip} />}
        </div>
        {oscillatorIndicators.length > 0 && (
          <>
            <div style={{ padding: "2px 8px", borderTop: "1px solid #2b3139", color: "#5e6673", fontSize: 10, fontFamily: "monospace", textTransform: "uppercase", letterSpacing: "0.05em" }}>
              {oscillatorIndicators.map((ind) => INDICATOR_LIBRARY[ind.type]?.shortLabel ?? ind.id).join(" · ")}
            </div>
            <div ref={oscillatorChartRef} style={{ height: 120, borderTop: "1px solid #2b3139", flexShrink: 0 }} />
          </>
        )}
      </div>
    );
  }

  const winCount = trades.filter((t) => String(t.executionType).startsWith("CLOSE") && t.realizedPnl > 0).length;
  const closeCount = trades.filter((t) => String(t.executionType).startsWith("CLOSE")).length;

  return (
    <div className="builder-chart-grid">
      <article className="builder-chart-card builder-chart-card--wide">
        <div className="builder-chart-card__header">
          <div>
            <span className="builder-section__eyebrow">3. Strategy Chart</span>
            <h3>{symbol} market replay</h3>
            <p>Overlay indicators, inspect entries and exits, and validate the rule path visually.</p>
          </div>
          <div className="builder-stats-inline">
            <div>
              <span>Trades</span>
              <strong>{trades.length}</strong>
            </div>
            <div>
              <span>Return</span>
              <strong>{formatPercent(result.totalReturn)}</strong>
            </div>
            <div>
              <span>Last Candle</span>
              <strong>{formatTime(candles[candles.length - 1]?.openTime ?? Date.now())}</strong>
            </div>
          </div>
        </div>

        <div className="builder-chart-surface-wrapper">
          <div className="builder-chart-card__surface" ref={marketChartRef} />
          <canvas className="builder-chart-shading-canvas" ref={shadingCanvasRef} />
          <ChartLegend items={legendValues} />
          {tooltip && <TradeTooltip tooltip={tooltip} />}
        </div>

        {oscillatorIndicators.length > 0 && (
          <div className="builder-chart-osc-label">
            {oscillatorIndicators.map((ind) => INDICATOR_LIBRARY[ind.type]?.shortLabel ?? ind.id).join(" · ")}
          </div>
        )}
        {oscillatorIndicators.length > 0 && (
          <div className="builder-chart-card__surface builder-chart-card__surface--oscillator" ref={oscillatorChartRef} />
        )}

        <div className="builder-chart-card__hint">
          Candles: {candles.length}. Interval: {interval}.
          {oscillatorIndicators.length > 0 ? ` Oscillators: ${oscillatorIndicators.length}.` : ""}
          {" "}Click a row in the trade ledger to zoom the chart.
        </div>
      </article>

      <article className="builder-chart-card">
        <div className="builder-chart-card__header">
          <div>
            <span className="builder-section__eyebrow">Equity Curve</span>
            <h3>Capital progression</h3>
          </div>
        </div>
        <div className="builder-chart-card__surface builder-chart-card__surface--short" ref={equityChartRef} />
      </article>

      <article className="builder-chart-card">
        <div className="builder-chart-card__header">
          <div>
            <span className="builder-section__eyebrow">Execution Pulse</span>
            <h3>Trade diagnostics</h3>
          </div>
        </div>
        <div className="builder-metric-list">
          <div className="builder-metric-list__item">
            <span>Initial Balance</span>
            <strong>{formatPrice(result.initialBalance)}</strong>
          </div>
          <div className="builder-metric-list__item">
            <span>Final Balance</span>
            <strong>{formatPrice(result.finalBalance)}</strong>
          </div>
          <div className="builder-metric-list__item">
            <span>Winning Closes</span>
            <strong>{closeCount ? `${winCount}/${closeCount}` : "0/0"}</strong>
          </div>
          <div className="builder-metric-list__item">
            <span>Chart Layers</span>
            <strong>{overlayIndicators.length + oscillatorIndicators.length}</strong>
          </div>
        </div>
      </article>
    </div>
  );
});

export default StrategyMarketChart;
