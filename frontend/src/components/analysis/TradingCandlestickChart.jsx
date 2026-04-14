import { useEffect, useMemo, useRef, useState } from "react";
import EmptyState from "../shared/EmptyState";
import { formatPrice, formatTime } from "../../utils/formatters";
import "./TradingCandlestickChart.css";

const MIN_SCALE = 60;
const MAX_SCALE = 220;
const SCALE_STEP = 10;
const MIN_VISIBLE_CANDLES = 18;
const MAX_VISIBLE_CANDLES = 140;

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function getVisibleCount(scale, totalCandles) {
  if (!totalCandles) {
    return 0;
  }

  const normalizedScale = (clamp(scale, MIN_SCALE, MAX_SCALE) - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
  const maxVisible = Math.min(totalCandles, MAX_VISIBLE_CANDLES);
  const minVisible = Math.min(totalCandles, MIN_VISIBLE_CANDLES);

  return Math.round(maxVisible - normalizedScale * (maxVisible - minVisible));
}

export default function TradingCandlestickChart({ candles, interval, onScaleChange, scale, symbol }) {
  const [hoveredIndex, setHoveredIndex] = useState(null);
  const [viewStart, setViewStart] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const dragStateRef = useRef(null);
  const zoomAnchorRef = useRef(null);
  const viewportRef = useRef(null);

  const visibleCount = useMemo(() => getVisibleCount(scale, candles.length), [candles.length, scale]);
  const maxViewStart = Math.max(candles.length - visibleCount, 0);

  useEffect(() => {
    setViewStart(maxViewStart);
  }, [candles, maxViewStart]);

  useEffect(() => {
    setViewStart((currentViewStart) => {
      const anchor = zoomAnchorRef.current;

      if (anchor) {
        zoomAnchorRef.current = null;
        return clamp(Math.round(anchor.absoluteIndex - anchor.ratio * (visibleCount - 1)), 0, maxViewStart);
      }

      return clamp(currentViewStart, 0, maxViewStart);
    });
  }, [maxViewStart, visibleCount]);

  useEffect(() => {
    const viewport = viewportRef.current;

    if (!viewport) {
      return undefined;
    }

    function preventViewportScroll(event) {
      event.preventDefault();
    }

    viewport.addEventListener("wheel", preventViewportScroll, { passive: false });

    return () => {
      viewport.removeEventListener("wheel", preventViewportScroll);
    };
  }, []);

  const chartModel = useMemo(() => {
    if (!candles.length || !visibleCount) {
      return null;
    }

    const visibleCandles = candles.slice(viewStart, viewStart + visibleCount);
    const width = 1280;
    const height = 580;
    const paddingTop = 42;
    const paddingBottom = 92;
    const paddingLeft = 132;
    const paddingRight = 68;
    const chartWidth = width - paddingLeft - paddingRight;
    const chartHeight = height - paddingTop - paddingBottom;
    const highs = visibleCandles.map((candle) => candle.high);
    const lows = visibleCandles.map((candle) => candle.low);
    const rawMaxPrice = Math.max(...highs);
    const rawMinPrice = Math.min(...lows);
    const rawPriceSpan = Math.max(rawMaxPrice - rawMinPrice, 1);
    const pricePadding = rawPriceSpan * 0.18;
    const maxPrice = rawMaxPrice + pricePadding;
    const minPrice = rawMinPrice - pricePadding;
    const priceSpan = Math.max(maxPrice - minPrice, 1);
    const candleGap = chartWidth / Math.max(visibleCandles.length, 1);
    const candleBodyWidth = clamp(candleGap * 0.62, 4, 20);
    const xTickCount = Math.min(6, visibleCandles.length);

    const priceToY = (price) => height - paddingBottom - ((price - minPrice) / priceSpan) * chartHeight;
    const xForIndex = (index) => paddingLeft + candleGap * index + candleGap / 2;

    const points = visibleCandles.map((candle, index) => {
      const x = xForIndex(index);
      return {
        absoluteIndex: viewStart + index,
        candle,
        closeY: priceToY(candle.close),
        highY: priceToY(candle.high),
        lowY: priceToY(candle.low),
        openY: priceToY(candle.open),
        x
      };
    });

    const yAxisTicks = Array.from({ length: 5 }, (_, index) => maxPrice - (priceSpan / 4) * index);
    const xAxisTicks = Array.from({ length: xTickCount }, (_, index) => {
      const pointIndex = Math.round((Math.max(visibleCandles.length - 1, 0) / Math.max(xTickCount - 1, 1)) * index);
      return points[pointIndex];
    }).filter(Boolean);

    return {
      candleBodyWidth,
      candleGap,
      chartHeight,
      chartWidth,
      height,
      latestPoint: points[points.length - 1],
      maxPrice,
      minPrice,
      paddingBottom,
      paddingLeft,
      paddingRight,
      paddingTop,
      points,
      priceToY,
      width,
      xAxisTicks,
      yAxisTicks
    };
  }, [candles, viewStart, visibleCount]);

  if (!chartModel) {
    return <EmptyState message="No historical candles available for analysis." />;
  }

  const hoveredPoint = hoveredIndex === null ? null : chartModel.points[hoveredIndex];
  const focusPoint = hoveredPoint ?? chartModel.latestPoint;
  const leftBoundary = chartModel.paddingLeft;
  const rightBoundary = chartModel.paddingLeft + chartModel.chartWidth;
  const focusChange = focusPoint.candle.close - focusPoint.candle.open;
  const focusChangePercent = focusPoint.candle.open === 0 ? 0 : (focusChange / focusPoint.candle.open) * 100;
  const focusTone = focusChange >= 0 ? "bullish" : "bearish";
  const visibleRatio = visibleCount / Math.max(candles.length, 1);
  const scrollThumbWidth = `${Math.max(visibleRatio * 100, 12)}%`;

  function updateHoveredPoint(event) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const pointerX = ((event.clientX - bounds.left) / bounds.width) * chartModel.width;
    const relativeX = clamp(pointerX - chartModel.paddingLeft, 0, chartModel.chartWidth);
    const approximateIndex = Math.floor(relativeX / chartModel.candleGap);
    const clampedIndex = clamp(approximateIndex, 0, chartModel.points.length - 1);
    setHoveredIndex(clampedIndex);
    return {
      absoluteIndex: viewStart + clampedIndex,
      ratio: chartModel.chartWidth === 0 ? 1 : relativeX / chartModel.chartWidth
    };
  }

  function applyScale(nextScale, anchor) {
    const clampedScale = clamp(nextScale, MIN_SCALE, MAX_SCALE);

    if (clampedScale === scale) {
      return;
    }

    zoomAnchorRef.current = anchor ?? null;
    onScaleChange(clampedScale);
  }

  function handlePointerMove(event) {
    updateHoveredPoint(event);

    if (!dragStateRef.current) {
      return;
    }

    const deltaX = dragStateRef.current.startX - event.clientX;
    const candleShift = Math.round(deltaX / chartModel.candleGap);
    setViewStart(clamp(dragStateRef.current.startViewStart + candleShift, 0, maxViewStart));
  }

  function handlePointerDown(event) {
    if (event.button !== 0) {
      return;
    }

    event.currentTarget.setPointerCapture(event.pointerId);
    dragStateRef.current = {
      startViewStart: viewStart,
      startX: event.clientX
    };
    setIsDragging(true);
  }

  function handlePointerUp(event) {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }

    dragStateRef.current = null;
    setIsDragging(false);
  }

  function handlePointerLeave() {
    dragStateRef.current = null;
    setIsDragging(false);
    setHoveredIndex(null);
  }

  function handleWheel(event) {
    event.preventDefault();
    const anchor = updateHoveredPoint(event);

    if (Math.abs(event.deltaX) > Math.abs(event.deltaY)) {
      const candleShift = Math.round(event.deltaX / Math.max(chartModel.candleGap, 1));
      setViewStart((currentViewStart) => clamp(currentViewStart + candleShift, 0, maxViewStart));
      return;
    }

    applyScale(scale + (event.deltaY < 0 ? SCALE_STEP : -SCALE_STEP), anchor);
  }

  return (
    <div className="analysis-chart">
      <div className="analysis-chart__meta">
        <div>
          <span className="analysis-chart__eyebrow">{symbol}</span>
          <h3 className="analysis-chart__title">Historical candle structure</h3>
        </div>

        <div className="analysis-chart__toolbar">
          <span className="analysis-chart__hint">Drag to pan. Scroll to scale.</span>
          <div className="analysis-chart__scale-controls">
            <button
              type="button"
              className="analysis-chart__scale-button"
              onClick={() => applyScale(scale - SCALE_STEP)}
              aria-label="Zoom out chart"
            >
              -
            </button>
            <span className="analysis-chart__scale-readout">{scale}%</span>
            <button
              type="button"
              className="analysis-chart__scale-button"
              onClick={() => applyScale(scale + SCALE_STEP)}
              aria-label="Zoom in chart"
            >
              +
            </button>
          </div>
          <div className="analysis-chart__interval">{interval.toUpperCase()}</div>
        </div>
      </div>

      <div className="analysis-chart__viewport" ref={viewportRef}>
        <svg
          className={`analysis-chart__surface ${isDragging ? "analysis-chart__surface--dragging" : ""}`}
          viewBox={`0 0 ${chartModel.width} ${chartModel.height}`}
          role="img"
          aria-label="Trading analysis candlestick chart"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerLeave}
          onPointerCancel={handlePointerUp}
          onWheel={handleWheel}
        >
          <rect
            x={chartModel.paddingLeft}
            y={chartModel.paddingTop}
            width={chartModel.chartWidth}
            height={chartModel.chartHeight}
            className="analysis-chart__pane"
          />

          {hoveredPoint ? (
            <g>
              <rect
                x={chartModel.paddingLeft + 18}
                y={chartModel.paddingTop + 14}
                width="442"
                height="92"
                rx="22"
                className="analysis-chart__info-panel"
              />
              <text className="analysis-chart__info-kicker" x={chartModel.paddingLeft + 38} y={chartModel.paddingTop + 38}>
                {formatTime(hoveredPoint.candle.openTime)}
              </text>
              <text
                className={`analysis-chart__info-change analysis-chart__info-change--${focusTone}`}
                x={chartModel.paddingLeft + 420}
                y={chartModel.paddingTop + 38}
                textAnchor="end"
              >
                {`${focusChange >= 0 ? "+" : ""}${formatPrice(focusChange)} (${focusChangePercent >= 0 ? "+" : ""}${focusChangePercent.toFixed(2)}%)`}
              </text>
              <g transform={`translate(${chartModel.paddingLeft + 38} ${chartModel.paddingTop + 54})`}>
                <rect x="0" y="0" width="92" height="34" rx="12" className="analysis-chart__info-cell" />
                <text className="analysis-chart__info-label" x="12" y="14">OPEN</text>
                <text className="analysis-chart__info-value" x="12" y="26">{formatPrice(hoveredPoint.candle.open)}</text>
              </g>
              <g transform={`translate(${chartModel.paddingLeft + 140} ${chartModel.paddingTop + 54})`}>
                <rect x="0" y="0" width="92" height="34" rx="12" className="analysis-chart__info-cell" />
                <text className="analysis-chart__info-label" x="12" y="14">HIGH</text>
                <text className="analysis-chart__info-value" x="12" y="26">{formatPrice(hoveredPoint.candle.high)}</text>
              </g>
              <g transform={`translate(${chartModel.paddingLeft + 242} ${chartModel.paddingTop + 54})`}>
                <rect x="0" y="0" width="92" height="34" rx="12" className="analysis-chart__info-cell" />
                <text className="analysis-chart__info-label" x="12" y="14">LOW</text>
                <text className="analysis-chart__info-value" x="12" y="26">{formatPrice(hoveredPoint.candle.low)}</text>
              </g>
              <g transform={`translate(${chartModel.paddingLeft + 344} ${chartModel.paddingTop + 54})`}>
                <rect x="0" y="0" width="78" height="34" rx="12" className="analysis-chart__info-cell" />
                <text className="analysis-chart__info-label" x="12" y="14">CLOSE</text>
                <text className="analysis-chart__info-value" x="12" y="26">{formatPrice(hoveredPoint.candle.close)}</text>
              </g>
            </g>
          ) : null}

          {chartModel.yAxisTicks.map((value) => {
            const y = chartModel.priceToY(value);
            return (
              <g key={value}>
                <line
                  x1={chartModel.paddingLeft}
                  y1={y}
                  x2={rightBoundary}
                  y2={y}
                  className="analysis-chart__gridline"
                />
                <text className="analysis-chart__axis-label" x={chartModel.paddingLeft - 18} y={y + 4} textAnchor="end">
                  {formatPrice(value)}
                </text>
              </g>
            );
          })}

          {chartModel.xAxisTicks.map((point) => (
            <g key={`x-axis-${point.candle.openTime}`}>
              <line
                x1={point.x}
                y1={chartModel.paddingTop}
                x2={point.x}
                y2={chartModel.height - chartModel.paddingBottom}
                className="analysis-chart__gridline analysis-chart__gridline--vertical"
              />
              <text
                className="analysis-chart__axis-label"
                x={point.x}
                y={chartModel.height - chartModel.paddingBottom + 24}
                textAnchor="middle"
              >
                {formatTime(point.candle.openTime)}
              </text>
            </g>
          ))}

          {chartModel.points.map((point) => {
            const isBullish = point.candle.close >= point.candle.open;
            const bodyTop = Math.min(point.openY, point.closeY);
            const bodyHeight = Math.max(Math.abs(point.closeY - point.openY), 2);

            return (
              <g key={point.candle.openTime}>
                <line
                  x1={point.x}
                  y1={point.highY}
                  x2={point.x}
                  y2={point.lowY}
                  className={`analysis-chart__wick ${isBullish ? "analysis-chart__wick--bullish" : "analysis-chart__wick--bearish"}`}
                />
                <rect
                  x={point.x - chartModel.candleBodyWidth / 2}
                  y={bodyTop}
                  width={chartModel.candleBodyWidth}
                  height={bodyHeight}
                  rx="2"
                  className={`analysis-chart__body ${isBullish ? "analysis-chart__body--bullish" : "analysis-chart__body--bearish"}`}
                />
                {hoveredPoint?.candle.openTime === point.candle.openTime ? (
                  <rect
                    x={point.x - chartModel.candleBodyWidth / 2 - 4}
                    y={Math.min(point.highY, bodyTop) - 6}
                    width={chartModel.candleBodyWidth + 8}
                    height={Math.max(point.lowY - Math.min(point.highY, bodyTop) + 12, bodyHeight + 12)}
                    rx="6"
                    className="analysis-chart__active-candle"
                  />
                ) : null}
              </g>
            );
          })}

          <line
            className="analysis-chart__price-line"
            x1={chartModel.paddingLeft}
            y1={focusPoint.closeY}
            x2={rightBoundary}
            y2={focusPoint.closeY}
          />

          {hoveredPoint ? (
            <g>
              <line
                className="analysis-chart__crosshair"
                x1={hoveredPoint.x}
                y1={chartModel.paddingTop}
                x2={hoveredPoint.x}
                y2={chartModel.height - chartModel.paddingBottom}
              />
              <line
                className="analysis-chart__crosshair"
                x1={chartModel.paddingLeft}
                y1={hoveredPoint.closeY}
                x2={rightBoundary}
                y2={hoveredPoint.closeY}
              />
              <circle cx={hoveredPoint.x} cy={hoveredPoint.closeY} r="5" className="analysis-chart__crosshair-point" />
            </g>
          ) : null}

          <g>
            <rect
              x="22"
              y={focusPoint.closeY - 18}
              width="96"
              height="36"
              rx="18"
              className="analysis-chart__price-tag"
            />
            <text className="analysis-chart__price-tag-text" x="70" y={focusPoint.closeY + 6} textAnchor="middle">
              {formatPrice(focusPoint.candle.close)}
            </text>
          </g>
        </svg>

        <div className="analysis-chart__scroll">
          <span className="analysis-chart__scroll-label">Scroll</span>
          <div
            className="analysis-chart__scroll-track"
            style={{
              "--scroll-window-left": maxViewStart > 0 ? `${(viewStart / maxViewStart) * (100 - Math.max(visibleRatio * 100, 12))}%` : "0%",
              "--scroll-window-width": scrollThumbWidth
            }}
          >
            <input
              className="analysis-chart__scroll-input"
              type="range"
              min="0"
              max={Math.max(maxViewStart, 0)}
              step="1"
              value={viewStart}
              onChange={(event) => setViewStart(Number(event.target.value))}
              aria-label="Horizontal chart scroll"
            />
            <div className="analysis-chart__scroll-window" aria-hidden="true" />
          </div>
        </div>
      </div>
    </div>
  );
}
