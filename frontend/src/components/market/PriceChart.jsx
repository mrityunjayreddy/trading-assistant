import { useState } from "react";
import EmptyState from "../shared/EmptyState";
import { formatPrice, formatTime } from "../../utils/formatters";
import "./PriceChart.css";

export default function PriceChart({ candles }) {
  const [hoveredIndex, setHoveredIndex] = useState(null);

  if (!candles.length) {
    return <EmptyState message="Waiting for market data to render the chart." />;
  }

  const width = 920;
  const height = 320;
  const paddingTop = 36;
  const paddingBottom = 44;
  const paddingLeft = 92;
  const paddingRight = 44;
  const closes = candles.map((candle) => candle.close);
  const volumes = candles.map((candle) => candle.volume);
  const rawMinPrice = Math.min(...closes);
  const rawMaxPrice = Math.max(...closes);
  const maxVolume = Math.max(...volumes, 1);
  const rawPriceSpan = Math.max(rawMaxPrice - rawMinPrice, 1);
  const pricePadding = rawPriceSpan * 0.28;
  const minPrice = rawMinPrice - pricePadding;
  const maxPrice = rawMaxPrice + pricePadding;
  const priceSpan = Math.max(maxPrice - minPrice, 1);
  const chartWidth = width - paddingLeft - paddingRight;
  const chartHeight = height - paddingTop - paddingBottom;
  const stepX = candles.length > 1 ? chartWidth / (candles.length - 1) : 0;
  const yAxisTicks = Array.from({ length: 5 }, (_, index) => maxPrice - (priceSpan / 4) * index);

  function pointsAt(sourceCandles, sourceMinPrice, sourcePriceSpan, sourceStepX) {
    return sourceCandles.map((candle, index) => {
      const x = paddingLeft + index * sourceStepX;
      const y = height - paddingBottom - ((candle.close - sourceMinPrice) / sourcePriceSpan) * chartHeight;
      return { x, y, candle };
    });
  }

  const points = pointsAt(candles, minPrice, priceSpan, stepX);
  const latestPoint = points.at(-1);
  const hoveredPoint = hoveredIndex === null ? null : points[hoveredIndex];
  const displayedPoint = hoveredPoint ?? latestPoint;

  const linePath = points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
  const areaPath = `${linePath} L ${points[points.length - 1].x} ${height - paddingBottom} L ${points[0].x} ${height - paddingBottom} Z`;

  function handlePointerMove(event) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const pointerX = ((event.clientX - bounds.left) / bounds.width) * width;
    const clampedX = Math.min(Math.max(pointerX, paddingLeft), width - paddingRight);
    const nextIndex = stepX === 0
      ? 0
      : Math.round((clampedX - paddingLeft) / stepX);

    setHoveredIndex(Math.min(Math.max(nextIndex, 0), points.length - 1));
  }

  return (
    <div className="chart-wrap">
      <svg
        className="chart-surface"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Price trend chart"
        onMouseMove={handlePointerMove}
        onMouseLeave={() => setHoveredIndex(null)}
      >
        <defs>
          <linearGradient id="price-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="rgba(97, 218, 251, 0.45)" />
            <stop offset="100%" stopColor="rgba(97, 218, 251, 0)" />
          </linearGradient>
        </defs>
        {yAxisTicks.map((value) => {
          const y = height - paddingBottom - ((value - minPrice) / priceSpan) * chartHeight;
          return (
            <g key={value}>
              <line
                x1={paddingLeft}
                y1={y}
                x2={width - paddingRight}
                y2={y}
                stroke="rgba(255,255,255,0.08)"
                strokeDasharray="4 8"
              />
              <line
                x1={paddingLeft - 8}
                y1={y}
                x2={paddingLeft - 2}
                y2={y}
                className="chart-axis-tick"
              />
              <text
                className="chart-axis-label"
                x={paddingLeft - 12}
                y={y + 4}
                textAnchor="end"
              >
                {formatPrice(value)}
              </text>
            </g>
          );
        })}
        <line
          className="chart-axis-line"
          x1={paddingLeft}
          y1={paddingTop}
          x2={paddingLeft}
          y2={height - paddingBottom}
        />
        {points.map((point) => (
          <rect
            key={point.candle.openTime}
            x={point.x - 2}
            y={height - paddingBottom - (point.candle.volume / maxVolume) * 56}
            width="4"
            height={(point.candle.volume / maxVolume) * 56}
            rx="2"
            fill="var(--volume)"
          />
        ))}
        <path d={areaPath} fill="url(#price-fill)" />
        <path
          d={linePath}
          fill="none"
          stroke="var(--line)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {displayedPoint ? (
          <>
            <line
              className="chart-crosshair"
              x1={displayedPoint.x}
              y1={paddingTop}
              x2={displayedPoint.x}
              y2={height - paddingBottom}
            />
            <line
              className="chart-crosshair"
              x1={paddingLeft}
              y1={displayedPoint.y}
              x2={width - paddingRight}
              y2={displayedPoint.y}
            />
            <circle
              cx={displayedPoint.x}
              cy={displayedPoint.y}
              r="5"
              fill="var(--line)"
            />
          </>
        ) : null}
        {displayedPoint ? (
          <g className="chart-price-callout">
            <rect
              x={Math.min(Math.max(displayedPoint.x - 82, paddingLeft), width - paddingRight - 122)}
              y={Math.max(displayedPoint.y - 56, paddingTop)}
              width="122"
              height="44"
              rx="16"
              fill="rgba(7, 17, 31, 0.9)"
              stroke="rgba(97, 218, 251, 0.45)"
            />
            <text
              className="chart-price-callout-text"
              x={Math.min(Math.max(displayedPoint.x - 21, paddingLeft + 61), width - paddingRight - 61)}
              y={Math.max(displayedPoint.y - 38, paddingTop + 14)}
              textAnchor="middle"
            >
              {formatPrice(displayedPoint.candle.close)}
            </text>
            <text
              className="chart-price-callout-subtext"
              x={Math.min(Math.max(displayedPoint.x - 21, paddingLeft + 61), width - paddingRight - 61)}
              y={Math.max(displayedPoint.y - 22, paddingTop + 28)}
              textAnchor="middle"
            >
              {formatTime(displayedPoint.candle.openTime)}
            </text>
          </g>
        ) : null}
      </svg>
      <div className="axis-row">
        <span>{formatTime(candles[0].openTime)}</span>
        <span>{candles.length} one-minute candles</span>
        <span>{formatTime(candles[candles.length - 1].openTime)}</span>
      </div>
    </div>
  );
}
