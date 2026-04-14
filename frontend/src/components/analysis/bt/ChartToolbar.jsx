import { INDICATOR_LIBRARY } from "../../../utils/strategyDsl";

const CHART_TYPES = [
  { value: "candlestick", label: "Candles" },
  { value: "line", label: "Line" },
  { value: "area", label: "Area" }
];

const INDICATOR_COLORS = [
  "#5badf7", "#f0b90b", "#c084fc", "#0ecb81", "#f6465d",
  "#ff9f43", "#54a0ff", "#00d2d3", "#ff6b6b", "#feca57"
];

export default function ChartToolbar({
  intervalOptions,
  selectedInterval,
  onIntervalChange,
  indicators,
  onRemoveIndicator,
  chartType,
  onChartTypeChange
}) {
  return (
    <div className="bt-chart-toolbar">
      {/* Timeframe buttons */}
      {intervalOptions.map((opt) => (
        <button
          key={opt.value}
          className={`bt-tf-btn ${opt.value === selectedInterval ? "bt-tf-btn--active" : ""}`}
          type="button"
          onClick={() => onIntervalChange(opt.value)}
        >
          {opt.label}
        </button>
      ))}

      {indicators.length > 0 && <div className="bt-toolbar-divider" />}

      {/* Active indicator chips */}
      <div className="bt-toolbar-chips">
        {indicators.map((ind, i) => {
          const def = INDICATOR_LIBRARY[ind.type];
          const color = ind.style?.color ?? INDICATOR_COLORS[i % INDICATOR_COLORS.length];
          const firstParam = def?.params?.find((p) => p.type === "number");
          const label = firstParam && ind.params?.[firstParam.name] != null
            ? `${def?.shortLabel ?? ind.type}(${ind.params[firstParam.name]})`
            : (def?.shortLabel ?? ind.type);

          return (
            <div key={ind.id} className="bt-ind-chip">
              <div className="bt-ind-chip__dot" style={{ background: color }} />
              <span>{label}</span>
              <button
                className="bt-ind-chip__remove"
                type="button"
                title={`Remove ${ind.id}`}
                onClick={() => onRemoveIndicator(ind.id)}
              >
                ×
              </button>
            </div>
          );
        })}
      </div>

      {/* Right controls */}
      <div className="bt-toolbar-right">
        <select
          className="bt-chart-type-select"
          value={chartType}
          onChange={(e) => onChartTypeChange(e.target.value)}
        >
          {CHART_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>
    </div>
  );
}
