import FieldTooltip from "./FieldTooltip";

export default function WorkspaceTopBar({
  actionsDisabled,
  availableSymbols,
  isCodeEditorOpen,
  intervalOptions,
  loading,
  onOpenCodeEditor,
  onRunSimulation,
  selectedInterval,
  selectedStrategy,
  selectedSymbol,
  setSelectedInterval,
  setSelectedStrategy,
  setSelectedSymbol,
  strategies
}) {
  return (
    <div className="tv-topbar">
      <div className="tv-topbar__group">
        <label className="tv-topbar__field">
          <span>
            Symbol
            <FieldTooltip text="Select the market used for the strategy run." />
          </span>
          <select className="tv-topbar__input" value={selectedSymbol} onChange={(event) => setSelectedSymbol(event.target.value)}>
            {availableSymbols.map((symbol) => (
              <option key={symbol} value={symbol}>{symbol}</option>
            ))}
          </select>
        </label>

        <label className="tv-topbar__field">
          <span>
            Interval
            <FieldTooltip text="Candle timeframe used for replay and backtests." />
          </span>
          <select className="tv-topbar__input" value={selectedInterval} onChange={(event) => setSelectedInterval(event.target.value)}>
            {intervalOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>

        <label className="tv-topbar__field tv-topbar__field--wide">
          <span>
            Preset
            <FieldTooltip text="Start from a preset, then refine the strategy visually or in JSON." />
          </span>
          <select className="tv-topbar__input" value={selectedStrategy} onChange={(event) => setSelectedStrategy(event.target.value)}>
            {strategies.map((strategy) => (
              <option key={strategy.value} value={strategy.value}>{strategy.label}</option>
            ))}
          </select>
        </label>
      </div>

      <div className="tv-topbar__group tv-topbar__group--actions">
        <button className={`tv-button tv-button--ghost ${isCodeEditorOpen ? "tv-button--active" : ""}`} type="button" onClick={onOpenCodeEditor}>
          {isCodeEditorOpen ? "Hide JSON" : "JSON Editor"}
        </button>
        <button className="tv-button tv-button--primary" type="button" onClick={onRunSimulation} disabled={loading || actionsDisabled}>
          {loading ? "Running..." : "Run Simulation"}
        </button>
      </div>
    </div>
  );
}
