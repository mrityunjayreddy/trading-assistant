import { useEffect, useMemo, useRef, useState } from "react";
import {
  AreaSeries,
  createChart,
  LineSeries
} from "lightweight-charts";
import { formatPercent, formatPrice, formatTime } from "../../../utils/formatters";
import {
  buildRuleSummary,
  INDICATOR_LIBRARY,
  RULE_OPERATOR_OPTIONS,
  VALUE_ONLY_OPERATORS,
  buildMissingIndicatorReferenceOptions
} from "../../../utils/strategyDsl";

// ── Equity curve chart ─────────────────────────────────────────────────────

function EquityCurve({ trades, initialBalance }) {
  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const seriesRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const chart = createChart(containerRef.current, {
      layout: { background: { color: "#0b0e11" }, textColor: "#848e9c" },
      grid: { vertLines: { color: "#2b3139" }, horzLines: { color: "#2b3139" } },
      rightPriceScale: { borderColor: "#2b3139" },
      timeScale: { borderColor: "#2b3139", timeVisible: true },
      handleScale: false,
      handleScroll: false,
    });
    const series = chart.addSeries(AreaSeries, {
      lineColor: "#0ecb81",
      topColor: "rgba(14,203,129,0.2)",
      bottomColor: "rgba(14,203,129,0.0)",
      lineWidth: 1,
      priceLineVisible: false,
    });
    chartRef.current = chart;
    seriesRef.current = series;

    const ro = new ResizeObserver(() => {
      chart.resize(containerRef.current.clientWidth, containerRef.current.clientHeight);
    });
    ro.observe(containerRef.current);
    return () => { ro.disconnect(); chart.remove(); };
  }, []);

  useEffect(() => {
    if (!seriesRef.current || !trades?.length) return;
    let balance = initialBalance ?? 1000;
    const points = trades
      .filter((t) => t.cashBalanceAfter != null)
      .map((t) => {
        balance = t.cashBalanceAfter;
        return { time: Math.floor(t.timestamp / 1000), value: balance };
      });
    // deduplicate timestamps
    const deduped = [];
    const seen = new Set();
    for (const p of points) {
      if (!seen.has(p.time)) { seen.add(p.time); deduped.push(p); }
    }
    if (deduped.length) seriesRef.current.setData(deduped);
  }, [trades, initialBalance]);

  return <div ref={containerRef} style={{ width: "100%", height: "100%" }} />;
}

// ── Summary metrics ────────────────────────────────────────────────────────

function computeSummaryMetrics(trades, initialBalance) {
  if (!trades?.length) return null;
  const closing = trades.filter((t) => (t.realizedPnl ?? 0) !== 0);
  if (!closing.length) return null;

  const wins = closing.filter((t) => t.realizedPnl > 0);
  const losses = closing.filter((t) => t.realizedPnl < 0);
  const bestPnl = Math.max(...closing.map((t) => t.realizedPnl));
  const worstPnl = Math.min(...closing.map((t) => t.realizedPnl));

  // Streaks
  let maxWin = 0, maxLoss = 0, curWin = 0, curLoss = 0;
  for (const t of closing) {
    if (t.realizedPnl > 0) { curWin++; curLoss = 0; maxWin = Math.max(maxWin, curWin); }
    else { curLoss++; curWin = 0; maxLoss = Math.max(maxLoss, curLoss); }
  }

  return {
    wins: wins.length, losses: losses.length, total: closing.length,
    bestPnl, worstPnl, maxWinStreak: maxWin, maxLossStreak: maxLoss,
    startCapital: initialBalance
  };
}

// ── Simple condition editor for BT layout ──────────────────────────────────

function CondRow({ node, index, total, parentId, refs, onUpdate, onRemove }) {
  const availableRefs = buildMissingIndicatorReferenceOptions(refs, [node.left, node.rightIndicator]);
  const isValueOnly = VALUE_ONLY_OPERATORS.has(node.operator);
  const isIndicatorMode = !isValueOnly && node.rightMode === "indicator";

  function upd(patch) { onUpdate(node.id, { ...node, ...patch }); }

  return (
    <div className="bt-cond-row">
      <div className="bt-cond-num">{index + 1}</div>
      <select
        className="bt-cond-select bt-cond-select--indicator"
        value={node.left}
        onChange={(e) => upd({ left: e.target.value })}
      >
        {availableRefs.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <select
        className="bt-cond-select"
        value={node.operator}
        onChange={(e) => upd({ operator: e.target.value })}
        style={{ maxWidth: 90 }}
      >
        {RULE_OPERATOR_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      {!isValueOnly && (
        <select
          className="bt-cond-select"
          value={node.rightMode}
          onChange={(e) => upd({ rightMode: e.target.value })}
          style={{ maxWidth: 60 }}
        >
          <option value="value">Val</option>
          <option value="indicator">Ind</option>
        </select>
      )}
      {isIndicatorMode ? (
        <select
          className="bt-cond-select bt-cond-select--indicator"
          value={node.rightIndicator}
          onChange={(e) => upd({ rightIndicator: e.target.value })}
        >
          <option value="">Select</option>
          {availableRefs.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      ) : (
        <input
          className="bt-cond-input"
          type="number"
          step="any"
          value={node.rightValue}
          onChange={(e) => upd({ rightValue: Number(e.target.value) })}
        />
      )}
      <button className="bt-cond-del" type="button" onClick={() => onRemove(node.id)}>×</button>
    </div>
  );
}

function LogicGroup({ scope, ruleTree, refs, actions }) {
  const isEntry = scope === "entryRules";
  const children = ruleTree?.children ?? [];
  const logic = ruleTree?.logicalOperator ?? "AND";

  return (
    <div className="bt-logic-group">
      <div className="bt-logic-group__hdr">
        <span className={isEntry ? "bt-badge-entry" : "bt-badge-exit"}>
          {isEntry ? "ENTRY" : "EXIT"}
        </span>
        <div className="bt-logic-pill">
          <button
            className={`bt-logic-pill-btn ${logic === "AND" ? "bt-logic-pill-btn--active" : ""}`}
            type="button"
            onClick={() => actions.updateRule(scope, ruleTree.id, () => ({ ...ruleTree, logicalOperator: "AND" }))}
          >AND</button>
          <button
            className={`bt-logic-pill-btn ${logic === "OR" ? "bt-logic-pill-btn--active" : ""}`}
            type="button"
            onClick={() => actions.updateRule(scope, ruleTree.id, () => ({ ...ruleTree, logicalOperator: "OR" }))}
          >OR</button>
        </div>
      </div>

      {children.map((child, i) => (
        <div key={child.id}>
          {i > 0 && (
            <div className="bt-cond-junction">
              <span className="bt-cond-junction-badge">{logic}</span>
            </div>
          )}
          {child.kind === "condition" && (
            <CondRow
              node={child}
              index={i}
              total={children.length}
              parentId={ruleTree.id}
              refs={refs}
              onUpdate={(id, next) => actions.updateRule(scope, id, () => next)}
              onRemove={(id) => actions.removeRule(scope, id)}
            />
          )}
        </div>
      ))}

      <button
        className="bt-add-cond-btn"
        type="button"
        onClick={() => actions.addRule(scope, ruleTree.id, "condition")}
      >
        + Add condition
      </button>
    </div>
  );
}

// ── Trade log ──────────────────────────────────────────────────────────────

function TradeLog({ trades, onTradeClick, highlightedId, onHover }) {
  if (!trades?.length) {
    return <div className="bt-empty">Run a simulation to see trade history.</div>;
  }

  return (
    <div className="bt-trade-table-wrap">
      <table className="bt-trade-table">
        <thead>
          <tr>
            <th>Date / Time</th>
            <th>Execution</th>
            <th>Dir</th>
            <th>Price</th>
            <th>Qty</th>
            <th>P&L</th>
            <th>Cash After</th>
          </tr>
        </thead>
        <tbody>
          {trades.map((t) => {
            const pnl = t.realizedPnl ?? 0;
            const key = `${t.timestamp}-${t.executionType}`;
            return (
              <tr
                key={key}
                className={highlightedId === key ? "highlighted" : ""}
                onClick={() => onTradeClick?.(t.timestamp)}
                onMouseEnter={() => onHover?.(key)}
                onMouseLeave={() => onHover?.(null)}
              >
                <td>{formatTime(t.timestamp)}</td>
                <td>{t.executionType}</td>
                <td>
                  <span className={`bt-dir-pill ${t.positionSide === "SHORT" ? "bt-dir-pill--short" : "bt-dir-pill--long"}`}>
                    {t.positionSide ?? "LONG"}
                  </span>
                </td>
                <td>{formatPrice(t.price)}</td>
                <td>{t.quantity?.toFixed(4)}</td>
                <td className={pnl > 0 ? "bt-pnl--pos" : pnl < 0 ? "bt-pnl--neg" : ""}>
                  {pnl !== 0 ? (pnl > 0 ? "+" : "") + formatPrice(pnl) : "—"}
                </td>
                <td>{formatPrice(t.cashBalanceAfter)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ── Summary tab ────────────────────────────────────────────────────────────

function SummaryTab({ result, summary }) {
  const trades = result?.trades ?? [];
  const m = useMemo(() => computeSummaryMetrics(trades, summary?.initialBalance), [trades, summary]);

  return (
    <div className="bt-summary-layout">
      <div className="bt-equity-chart">
        <EquityCurve trades={trades} initialBalance={summary?.initialBalance} />
      </div>
      <div className="bt-summary-metrics">
        {[
          { label: "Start Capital", val: formatPrice(summary?.initialBalance) },
          { label: "End Capital", val: formatPrice(summary?.finalBalance) },
          { label: "Total Return", val: formatPercent((summary?.totalReturn ?? 0) * 100) },
          { label: "Trades", val: summary?.tradesCount ?? 0 },
          { label: "Wins", val: m?.wins ?? "—" },
          { label: "Losses", val: m?.losses ?? "—" },
          { label: "Best Trade", val: m ? formatPrice(m.bestPnl) : "—" },
          { label: "Worst Trade", val: m ? formatPrice(m.worstPnl) : "—" },
          { label: "Max Win Streak", val: m?.maxWinStreak ?? "—" },
          { label: "Max Loss Streak", val: m?.maxLossStreak ?? "—" }
        ].map(({ label, val }) => (
          <div key={label} className="bt-metric-row">
            <span className="bt-metric-label">{label}</span>
            <span className="bt-metric-val">{val}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Main BottomPanel ───────────────────────────────────────────────────────

export default function BottomPanel({
  entryRules,
  exitRules,
  indicatorReferences,
  builderActions,
  result,
  summary,
  onTradeClick
}) {
  const [activeTab, setActiveTab] = useState("logic");
  const [highlightedId, setHighlightedId] = useState(null);

  const trades = Array.isArray(result?.trades) ? result.trades : [];
  const tradeCount = trades.length;

  // Plain-English summary
  const entrySummary = buildRuleSummary(entryRules, indicatorReferences);
  const exitSummary = buildRuleSummary(exitRules, indicatorReferences);

  const summaryText = entrySummary || exitSummary
    ? <>
        <span className="hl-long">LONG</span> when{" "}
        <span className="hl-indicator">{entrySummary ?? "—"}</span>
        {" → Exit when "}
        <span className="hl-indicator">{exitSummary ?? "—"}</span>
      </>
    : "No conditions defined yet.";

  return (
    <div className="bt-bottom">
      {/* Tab strip */}
      <div className="bt-tabs">
        <button
          className={`bt-tab ${activeTab === "logic" ? "bt-tab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("logic")}
        >
          Visual Logic
        </button>
        <button
          className={`bt-tab ${activeTab === "trades" ? "bt-tab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("trades")}
        >
          Trade Log {tradeCount > 0 && `(${tradeCount})`}
        </button>
        <button
          className={`bt-tab ${activeTab === "summary" ? "bt-tab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("summary")}
        >
          Summary
        </button>
      </div>

      {/* Tab content */}
      <div className="bt-tab-content">
        {activeTab === "logic" && (
          <div className="bt-logic">
            <div className="bt-logic-summary">{summaryText}</div>
            <div className="bt-logic-groups">
              <LogicGroup
                scope="entryRules"
                ruleTree={entryRules}
                refs={indicatorReferences}
                actions={builderActions}
              />
              <LogicGroup
                scope="exitRules"
                ruleTree={exitRules}
                refs={indicatorReferences}
                actions={builderActions}
              />
            </div>
          </div>
        )}

        {activeTab === "trades" && (
          <TradeLog
            trades={trades}
            onTradeClick={onTradeClick}
            highlightedId={highlightedId}
            onHover={setHighlightedId}
          />
        )}

        {activeTab === "summary" && (
          <SummaryTab result={result} summary={summary} />
        )}
      </div>
    </div>
  );
}
