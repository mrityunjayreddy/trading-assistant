import { useEffect, useRef, useState } from "react";
import { formatPercent, formatPrice } from "../../../utils/formatters";

const INTERVAL_LABELS = {
  "1m": "1m", "3m": "3m", "5m": "5m", "15m": "15m", "30m": "30m",
  "1h": "1h", "2h": "2h", "4h": "4h", "6h": "6h", "12h": "12h",
  "1d": "1D", "3d": "3D", "1w": "1W"
};

export default function BacktestTopbar({
  availableSymbols,
  selectedSymbol,
  onSymbolChange,
  intervalOptions,
  selectedInterval,
  onIntervalChange,
  range,
  onRangeChange,
  assumptions,
  onAssumptionChange,
  strategies,
  selectedStrategy,
  onStrategyChange,
  summary,
  loading,
  canRun,
  onRun,
  isJsonOpen,
  onToggleJson,
  onBack
}) {
  const [symbolOpen, setSymbolOpen] = useState(false);
  const [symbolSearch, setSymbolSearch] = useState("");
  const dropRef = useRef(null);

  // Close dropdown on outside click
  useEffect(() => {
    if (!symbolOpen) return;
    function handler(e) {
      if (dropRef.current && !dropRef.current.contains(e.target)) setSymbolOpen(false);
    }
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [symbolOpen]);

  const filteredSymbols = availableSymbols.filter((s) =>
    s.toLowerCase().includes(symbolSearch.toLowerCase())
  );

  const totalReturn = summary?.totalReturn ?? 0;
  const priceChange = totalReturn * 100;

  return (
    <div className="bt-topbar">
      {/* Brand / back */}
      <button className="bt-topbar__brand" type="button" onClick={onBack} title="Back to Dashboard">
        ← Studio
      </button>

      {/* Symbol selector */}
      <div style={{ position: "relative" }} ref={dropRef}>
        <button
          className="bt-topbar__symbol-btn"
          type="button"
          onClick={() => { setSymbolOpen((v) => !v); setSymbolSearch(""); }}
        >
          {selectedSymbol || "Select"} <span style={{ color: "var(--bt-text3)", fontSize: 10 }}>▾</span>
        </button>

        {symbolOpen && (
          <div className="bt-symbol-dropdown">
            <input
              autoFocus
              className="bt-symbol-search"
              placeholder="Search symbol…"
              value={symbolSearch}
              onChange={(e) => setSymbolSearch(e.target.value)}
            />
            <div className="bt-symbol-list">
              {filteredSymbols.map((s) => (
                <button
                  key={s}
                  type="button"
                  className={s === selectedSymbol ? "active" : ""}
                  onClick={() => { onSymbolChange(s); setSymbolOpen(false); }}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Price / return */}
      <div className="bt-topbar__price">
        <span className="bt-topbar__price-val">
          {summary?.finalBalance ? formatPrice(summary.finalBalance) : "—"}
        </span>
        <span className={`bt-topbar__price-chg bt-topbar__price-chg--${priceChange >= 0 ? "pos" : "neg"}`}>
          {priceChange >= 0 ? "+" : ""}{formatPercent(priceChange)}
        </span>
      </div>

      {/* Controls */}
      <div className="bt-topbar__controls">
        {/* Interval */}
        <div className="bt-topbar__field">
          <span>Interval</span>
          <select
            className="bt-topbar__select"
            value={selectedInterval}
            onChange={(e) => onIntervalChange(e.target.value)}
          >
            {intervalOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{INTERVAL_LABELS[opt.value] ?? opt.label}</option>
            ))}
          </select>
        </div>

        {/* Date range */}
        <div className="bt-topbar__field">
          <span>From</span>
          <input
            className="bt-topbar__select"
            type="date"
            value={range.start ? range.start.slice(0, 10) : ""}
            onChange={(e) => onRangeChange("start", e.target.value + "T00:00")}
            style={{ width: 96 }}
          />
        </div>

        <div className="bt-topbar__field">
          <span>To</span>
          <input
            className="bt-topbar__select"
            type="date"
            value={range.end ? range.end.slice(0, 10) : ""}
            onChange={(e) => onRangeChange("end", e.target.value + "T23:59")}
            style={{ width: 96 }}
          />
        </div>

        {/* Capital */}
        <div className="bt-topbar__field">
          <span>Capital</span>
          <input
            className="bt-topbar__input bt-topbar__input--capital"
            type="number"
            step="100"
            value={assumptions.initialBalance ?? 1000}
            onChange={(e) => onAssumptionChange("initialBalance", Number(e.target.value))}
          />
        </div>

        {/* Commission */}
        <div className="bt-topbar__field">
          <span>Fee</span>
          <div className="bt-topbar__commission-wrap">
            <input
              className="bt-topbar__input bt-topbar__input--commission"
              type="number"
              step="0.0001"
              min="0"
              max="0.1"
              value={assumptions.feeRate ?? 0.0004}
              onChange={(e) => onAssumptionChange("feeRate", Number(e.target.value))}
            />
            <span>rate</span>
          </div>
        </div>

        {/* Preset */}
        {strategies?.length > 0 && (
          <div className="bt-topbar__field">
            <span>Preset</span>
            <select
              className="bt-topbar__select"
              value={selectedStrategy}
              onChange={(e) => onStrategyChange(e.target.value)}
              style={{ maxWidth: 110 }}
            >
              {strategies.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Action buttons */}
      <div className="bt-topbar__actions">
        <button
          className={`bt-btn bt-btn--ghost ${isJsonOpen ? "bt-btn--active" : ""}`}
          type="button"
          onClick={onToggleJson}
        >
          JSON
        </button>
        <button
          className="bt-btn bt-btn--run"
          type="button"
          onClick={onRun}
          disabled={!canRun}
        >
          {loading ? <span className="bt-spinner" /> : "▶"} Run
        </button>
      </div>
    </div>
  );
}
