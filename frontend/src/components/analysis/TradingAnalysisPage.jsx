import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import "./bt/BacktestShell.css";
import BacktestTopbar from "./bt/BacktestTopbar";
import BottomPanel from "./bt/BottomPanel";
import ChartToolbar from "./bt/ChartToolbar";
import IndicatorBrowser from "./bt/IndicatorBrowser";
import IndicatorParams from "./bt/IndicatorParams";
import StatsBar from "./bt/StatsBar";
import StrategyMarketChart from "./builder/StrategyMarketChart";
import { hydrateBuilderStateFromDsl, validateBuilderState } from "../../utils/strategyDsl";

export default function TradingAnalysisPage({ analysisData, onBack }) {
  const {
    assumptions,
    availableSymbols,
    builderActions,
    builderState,
    dslPreview,
    errorMessage,
    executionModelDefinition,
    executionModels,
    executionParams,
    indicatorOptions,
    indicatorReferenceOptions,
    intervalOptions,
    loading,
    range,
    selectedExecutionModel,
    selectedInterval,
    selectedStrategy,
    selectedSymbol,
    selectedTradeDirection,
    simulationResult,
    strategies,
    strategyDefinition,
    strategyParams,
    summary,
    tradeDirectionOptions,
    runSimulation,
    setAnalysisMode,
    setSelectedExecutionModel,
    setSelectedInterval,
    setSelectedStrategy,
    setSelectedSymbol,
    setSelectedTradeDirection,
    updateAssumption,
    updateExecutionParam,
    updateRange,
    updateStrategyParam,
    validationMessage
  } = analysisData;

  const chartRef = useRef(null);
  const [chartType, setChartType] = useState("candlestick");
  const [isJsonOpen, setIsJsonOpen] = useState(false);
  const [jsonDraft, setJsonDraft] = useState(dslPreview);
  const [jsonDirty, setJsonDirty] = useState(false);
  const [jsonSyncEnabled, setJsonSyncEnabled] = useState(true);
  const [jsonApplyError, setJsonApplyError] = useState("");

  const canRun = Boolean(selectedSymbol) && !loading && !validationMessage;

  // Sync JSON draft when DSL changes externally
  useEffect(() => {
    if (!jsonDirty || jsonSyncEnabled) {
      setJsonDraft(dslPreview);
      if (!jsonDirty) setJsonApplyError("");
    }
  }, [dslPreview, jsonDirty, jsonSyncEnabled]);

  const jsonValidationMessages = useMemo(() => {
    try {
      const parsed = JSON.parse(jsonDraft);
      return validateBuilderState(hydrateBuilderStateFromDsl(parsed));
    } catch { return []; }
  }, [jsonDraft]);

  function handleRun() {
    if (!canRun) return;
    setAnalysisMode("SIMULATION");
    runSimulation();
  }

  function handleJsonApply() {
    try {
      const parsed = JSON.parse(jsonDraft);
      const hydrated = hydrateBuilderStateFromDsl(parsed);
      const errs = validateBuilderState(hydrated);
      if (errs.length) { setJsonApplyError(errs[0]); return; }
      // Clear dirty flag BEFORE hydrating so the sync useEffect (which fires
      // when dslPreview changes) does not treat the incoming re-serialisation
      // as an external change and overwrite the textarea a second time.
      setJsonApplyError("");
      setJsonDirty(false);
      builderActions.hydrateFromDsl(parsed);
      setAnalysisMode("SIMULATION");
    } catch (e) { setJsonApplyError(e.message); }
  }

  const handleTradeClick = useCallback((timestamp) => {
    chartRef.current?.scrollToTime(timestamp);
  }, []);

  return (
    <div className="bt-shell">
      {/* ── Topbar ── */}
      <BacktestTopbar
        availableSymbols={availableSymbols}
        selectedSymbol={selectedSymbol}
        onSymbolChange={setSelectedSymbol}
        intervalOptions={intervalOptions}
        selectedInterval={selectedInterval}
        onIntervalChange={setSelectedInterval}
        range={range}
        onRangeChange={updateRange}
        assumptions={assumptions}
        onAssumptionChange={updateAssumption}
        strategies={strategies}
        selectedStrategy={selectedStrategy}
        onStrategyChange={setSelectedStrategy}
        summary={summary}
        loading={loading}
        canRun={canRun}
        onRun={handleRun}
        isJsonOpen={isJsonOpen}
        onToggleJson={() => setIsJsonOpen((v) => !v)}
        onBack={onBack}
      />

      {/* ── Body ── */}
      <div className="bt-body">
        {/* LEFT — indicator browser */}
        <IndicatorBrowser
          indicators={builderState.indicators}
          onAddIndicator={builderActions.addIndicator}
          onRemoveIndicator={builderActions.removeIndicator}
        />

        {/* CENTER — chart + stats + bottom */}
        <div className="bt-center">
          {/* Chart toolbar */}
          <ChartToolbar
            intervalOptions={intervalOptions}
            selectedInterval={selectedInterval}
            onIntervalChange={setSelectedInterval}
            indicators={builderState.indicators}
            onRemoveIndicator={builderActions.removeIndicator}
            chartType={chartType}
            onChartTypeChange={setChartType}
          />

          {/* Chart area */}
          <div className="bt-chart-area">
            {isJsonOpen ? (
              <div className="bt-json-overlay">
                {/* Header */}
                <div className="bt-json-header">
                  <span className="bt-json-title">Strategy JSON</span>
                  <span className={`bt-json-status ${jsonApplyError ? "bt-json-status--error" : jsonDirty ? "bt-json-status--dirty" : "bt-json-status--ok"}`}>
                    {jsonApplyError ? "Error" : jsonDirty ? "Unsaved changes" : "Synced"}
                  </span>
                  <div className="bt-json-actions">
                    <button
                      className={`bt-btn ${jsonSyncEnabled ? "bt-btn--active" : ""}`}
                      type="button"
                      title="Auto-sync JSON when builder changes"
                      onClick={() => setJsonSyncEnabled((v) => !v)}
                    >
                      Auto-sync {jsonSyncEnabled ? "ON" : "OFF"}
                    </button>
                    <button
                      className="bt-btn"
                      type="button"
                      onClick={() => { setJsonDraft(dslPreview); setJsonApplyError(""); setJsonDirty(false); }}
                      disabled={!jsonDirty}
                    >
                      Reset
                    </button>
                    <button
                      className="bt-btn bt-btn--run"
                      type="button"
                      onClick={handleJsonApply}
                      disabled={!jsonDirty}
                    >
                      Apply
                    </button>
                    <button
                      className="bt-btn"
                      type="button"
                      onClick={() => setIsJsonOpen(false)}
                    >
                      ✕ Close
                    </button>
                  </div>
                </div>

                {/* Body */}
                <div className="bt-json-body">
                  <textarea
                    className={`bt-json-textarea${jsonApplyError ? " bt-json-textarea--error" : ""}`}
                    value={jsonDraft}
                    onChange={(e) => { setJsonDraft(e.target.value); setJsonDirty(true); setJsonApplyError(""); }}
                    spellCheck={false}
                  />
                  <div className="bt-json-footer">
                    {jsonApplyError && (
                      <div className="bt-json-error">{jsonApplyError}</div>
                    )}
                    {!jsonApplyError && jsonValidationMessages.length > 0 && (
                      <div className="bt-json-validation">{jsonValidationMessages[0]}</div>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <StrategyMarketChart
                ref={chartRef}
                btMode
                dslState={builderState}
                interval={selectedInterval}
                result={simulationResult}
                symbol={selectedSymbol}
              />
            )}

            {/* Error / validation banners */}
            {(validationMessage || errorMessage) && (
              <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, zIndex: 30 }}>
                {validationMessage && <div className="bt-banner">{validationMessage}</div>}
                {errorMessage && <div className="bt-banner">{errorMessage}</div>}
              </div>
            )}
          </div>

          {/* Stats bar */}
          <StatsBar result={simulationResult} summary={summary} />

          {/* Bottom panel */}
          <BottomPanel
            entryRules={builderState.entryRules}
            exitRules={builderState.exitRules}
            indicatorReferences={indicatorReferenceOptions}
            builderActions={{
              addRule: builderActions.addRule,
              removeRule: builderActions.removeRule,
              updateRule: builderActions.updateRule
            }}
            result={simulationResult}
            summary={summary}
            onTradeClick={handleTradeClick}
          />
        </div>

        {/* RIGHT — indicator params */}
        <IndicatorParams
          indicators={builderState.indicators}
          inputReferences={indicatorReferenceOptions}
          onUpdateIndicator={(id, next) => builderActions.updateIndicator(id, () => next)}
          onRemoveIndicator={builderActions.removeIndicator}
          executionModels={executionModels}
          selectedExecutionModel={selectedExecutionModel}
          onExecutionModelChange={setSelectedExecutionModel}
          tradeDirectionOptions={tradeDirectionOptions}
          selectedTradeDirection={selectedTradeDirection}
          onTradeDirectionChange={setSelectedTradeDirection}
          executionModelDefinition={executionModelDefinition}
          executionParams={executionParams}
          onExecutionParamChange={updateExecutionParam}
          assumptions={assumptions}
          onAssumptionChange={updateAssumption}
          range={range}
          onRangeChange={updateRange}
        />
      </div>
    </div>
  );
}
