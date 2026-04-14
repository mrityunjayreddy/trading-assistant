import { useEffect, useMemo, useRef, useState } from "react";
import Panel from "../../shared/Panel";
import { formatPercent, formatPrice, formatTime } from "../../../utils/formatters";

const PAGE_SIZE_OPTIONS = [10, 25, 50];

// ── Count-up animation hook ────────────────────────────────────────────────

function useCountUp(target, trigger) {
  const [value, setValue] = useState(0);
  const rafRef = useRef(null);

  useEffect(() => {
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    if (!target) { setValue(0); return; }

    let startTs = null;
    const duration = 750;

    function step(ts) {
      if (!startTs) startTs = ts;
      const progress = Math.min((ts - startTs) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
      setValue(target * eased);
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(step);
      } else {
        setValue(target);
      }
    }

    rafRef.current = requestAnimationFrame(step);
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current); };
  }, [trigger]); // eslint-disable-line react-hooks/exhaustive-deps

  return value;
}

// ── Derived metrics from trade ledger ─────────────────────────────────────

function computeMetrics(trades) {
  if (!trades.length) return null;

  const closingTrades = trades.filter((t) => (t.realizedPnl ?? 0) !== 0);
  if (!closingTrades.length) return null;

  const wins = closingTrades.filter((t) => t.realizedPnl > 0);
  const winRate = wins.length / closingTrades.length;

  const grossProfit = wins.reduce((sum, t) => sum + t.realizedPnl, 0);
  const grossLoss = Math.abs(
    closingTrades.filter((t) => t.realizedPnl < 0).reduce((sum, t) => sum + t.realizedPnl, 0)
  );
  const profitFactor = grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? Infinity : 0;

  const bestPnl = Math.max(...closingTrades.map((t) => t.realizedPnl));
  const worstPnl = Math.min(...closingTrades.map((t) => t.realizedPnl));

  // Max drawdown — only use trades where cashBalanceAfter is defined (CLOSE/SELL trades).
  // BUY trades have null cashBalanceAfter; falling back to 0 creates phantom 100% drawdowns.
  let peak = -Infinity;
  let maxDrawdown = 0;
  for (const t of trades) {
    if (t.cashBalanceAfter == null) continue;
    const bal = t.cashBalanceAfter;
    if (bal > peak) peak = bal;
    if (peak > 0) {
      const dd = (peak - bal) / peak;
      if (dd > maxDrawdown) maxDrawdown = dd;
    }
  }

  // Avg trade duration by pairing OPEN → CLOSE in order
  const durations = [];
  const openTimestamps = [];
  for (const t of trades) {
    const type = (t.executionType ?? "").toUpperCase();
    if (type === "OPEN" || type === "BUY") {
      openTimestamps.push(t.timestamp);
    } else if ((type === "CLOSE" || type === "SELL") && openTimestamps.length) {
      durations.push(t.timestamp - openTimestamps.shift());
    }
  }
  const avgDurationMs = durations.length
    ? durations.reduce((a, b) => a + b, 0) / durations.length
    : null;

  return { winRate, profitFactor, bestPnl, worstPnl, maxDrawdown, avgDurationMs };
}

function formatDuration(ms) {
  if (ms == null) return "—";
  const totalSeconds = Math.round(ms / 1000);
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.round(totalSeconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = (hours / 24).toFixed(1);
  return `${days}d`;
}

// ── Stat cards ─────────────────────────────────────────────────────────────

function StatCard({ label, value, hint }) {
  return (
    <Panel className="tv-stat-card">
      <span className="tv-panel__eyebrow">{label}</span>
      <strong>{value}</strong>
      <p>{hint}</p>
    </Panel>
  );
}

function StatsBar({ metrics }) {
  if (!metrics) return null;

  const items = [
    {
      label: "Win Rate",
      value: formatPercent(metrics.winRate),
      positive: metrics.winRate >= 0.5
    },
    {
      label: "Max Drawdown",
      value: formatPercent(metrics.maxDrawdown),
      positive: metrics.maxDrawdown < 0.1
    },
    {
      label: "Profit Factor",
      value: isFinite(metrics.profitFactor) ? metrics.profitFactor.toFixed(2) : "∞",
      positive: metrics.profitFactor >= 1
    },
    {
      label: "Best Trade",
      value: formatPrice(metrics.bestPnl),
      positive: metrics.bestPnl > 0
    },
    {
      label: "Worst Trade",
      value: formatPrice(metrics.worstPnl),
      positive: metrics.worstPnl >= 0
    },
    {
      label: "Avg Duration",
      value: formatDuration(metrics.avgDurationMs),
      positive: null
    }
  ];

  return (
    <div className="tv-stats-bar">
      {items.map((item) => (
        <div key={item.label} className="tv-stats-bar__item">
          <span className="tv-stats-bar__label">{item.label}</span>
          <span
            className={[
              "tv-stats-bar__value",
              item.positive === true ? "tv-stats-bar__value--pos" : "",
              item.positive === false ? "tv-stats-bar__value--neg" : ""
            ].join(" ").trim()}
          >
            {item.value}
          </span>
        </div>
      ))}
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────

export default function TradingWorkbenchResults({
  result,
  summary,
  onTradeClick
}) {
  const [tradePage, setTradePage] = useState(1);
  const [tradePageSize, setTradePageSize] = useState(10);

  const trades = Array.isArray(result?.trades) ? result.trades : [];
  const totalTradePages = Math.max(1, Math.ceil(trades.length / tradePageSize));
  const paginatedTrades = useMemo(() => {
    const startIndex = (tradePage - 1) * tradePageSize;
    return trades.slice(startIndex, startIndex + tradePageSize);
  }, [tradePage, tradePageSize, trades]);

  useEffect(() => { setTradePage(1); }, [result, tradePageSize]);

  useEffect(() => {
    if (tradePage > totalTradePages) setTradePage(totalTradePages);
  }, [totalTradePages, tradePage]);

  // Count-up animations — trigger resets when result changes
  const animBalance = useCountUp(summary.finalBalance ?? 0, result);
  const animReturn = useCountUp((summary.totalReturn ?? 0) * 100, result);
  const animTrades = useCountUp(summary.tradesCount ?? 0, result);

  const metrics = useMemo(() => computeMetrics(trades), [trades]);

  return (
    <div className="tv-results">
      <div className="tv-results__stats">
        <StatCard
          label="Final Balance"
          value={formatPrice(animBalance)}
          hint={`Started at ${formatPrice(summary.initialBalance)}`}
        />
        <StatCard
          label="Total Return"
          value={`${animReturn >= 0 ? "+" : ""}${animReturn.toFixed(2)}%`}
          hint="Simulation result"
        />
        <StatCard
          label="Trades"
          value={Math.round(animTrades)}
          hint="Executed orders"
        />
      </div>

      <StatsBar metrics={metrics} />

      <Panel className="tv-results__panel">
        <div className="tv-panel__header">
          <div>
            <span className="tv-panel__eyebrow">Trade Ledger</span>
            <h3>Executions</h3>
            <p>Inspect the fills generated by the latest simulation run.</p>
          </div>
          <div className="tv-table-toolbar">
            <label className="tv-table-toolbar__field">
              <span>Rows</span>
              <select
                className="tv-input tv-input--compact"
                value={tradePageSize}
                onChange={(event) => setTradePageSize(Number(event.target.value))}
              >
                {PAGE_SIZE_OPTIONS.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </label>

            <div className="tv-table-toolbar__pager">
              <button
                className="tv-chip-button"
                type="button"
                onClick={() => setTradePage((current) => Math.max(1, current - 1))}
                disabled={tradePage === 1}
              >
                Prev
              </button>
              <span>Page {tradePage} / {totalTradePages}</span>
              <button
                className="tv-chip-button"
                type="button"
                onClick={() => setTradePage((current) => Math.min(totalTradePages, current + 1))}
                disabled={tradePage === totalTradePages}
              >
                Next
              </button>
            </div>
          </div>
        </div>

        {trades.length ? (
          <div className="tv-table-shell">
            <table className="tv-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Execution</th>
                  <th>Side</th>
                  <th>Price</th>
                  <th>Qty</th>
                  <th>PnL</th>
                  <th>Cash After</th>
                </tr>
              </thead>
              <tbody>
                {paginatedTrades.map((trade) => (
                  <tr
                    key={`${trade.timestamp}-${trade.executionType}`}
                    className={onTradeClick ? "tv-table__row--clickable" : ""}
                    onClick={onTradeClick ? () => onTradeClick(trade.timestamp) : undefined}
                    title={onTradeClick ? "Click to zoom chart to this trade" : undefined}
                  >
                    <td>{formatTime(trade.timestamp)}</td>
                    <td>{trade.executionType}</td>
                    <td>{trade.positionSide}</td>
                    <td>{formatPrice(trade.price)}</td>
                    <td>{trade.quantity.toFixed(4)}</td>
                    <td
                      className={
                        (trade.realizedPnl ?? 0) > 0
                          ? "tv-pnl--pos"
                          : (trade.realizedPnl ?? 0) < 0
                            ? "tv-pnl--neg"
                            : ""
                      }
                    >
                      {formatPrice(trade.realizedPnl ?? 0)}
                    </td>
                    <td>{formatPrice(trade.cashBalanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="tv-empty-state">Run a simulation to populate the results section.</div>
        )}
      </Panel>
    </div>
  );
}
