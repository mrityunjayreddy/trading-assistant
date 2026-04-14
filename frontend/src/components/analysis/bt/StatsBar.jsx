import { useEffect, useMemo, useRef, useState } from "react";
import { formatPercent, formatPrice } from "../../../utils/formatters";

// ── Count-up hook ──────────────────────────────────────────────────────────

function useCountUp(target, trigger) {
  const [value, setValue] = useState(0);
  const rafRef = useRef(null);

  useEffect(() => {
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    if (!target) { setValue(0); return; }
    let startTs = null;
    const duration = 800;
    function step(ts) {
      if (!startTs) startTs = ts;
      const t = Math.min((ts - startTs) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(target * eased);
      if (t < 1) rafRef.current = requestAnimationFrame(step);
      else setValue(target);
    }
    rafRef.current = requestAnimationFrame(step);
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current); };
  }, [trigger]); // eslint-disable-line react-hooks/exhaustive-deps

  return value;
}

// ── Metric computation ─────────────────────────────────────────────────────

function computeStats(trades) {
  if (!trades?.length) return null;

  const closing = trades.filter((t) => (t.realizedPnl ?? 0) !== 0);
  if (!closing.length) return null;

  const wins = closing.filter((t) => t.realizedPnl > 0);
  const losses = closing.filter((t) => t.realizedPnl < 0);

  const winRate = wins.length / closing.length;
  const grossProfit = wins.reduce((s, t) => s + t.realizedPnl, 0);
  const grossLoss = Math.abs(losses.reduce((s, t) => s + t.realizedPnl, 0));
  const profitFactor = grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? 99 : 0;

  // Max drawdown — only use trades where cashBalanceAfter is defined (CLOSE/SELL trades).
  // BUY/OPEN trades have null cashBalanceAfter (cash is deployed into the position)
  // and falling back to 0 would create phantom 100% drawdown readings.
  const equityPoints = trades.filter((t) => t.cashBalanceAfter != null);
  let peak = -Infinity, maxDD = 0;
  for (const t of equityPoints) {
    const b = t.cashBalanceAfter;
    if (b > peak) peak = b;
    if (peak > 0) maxDD = Math.max(maxDD, (peak - b) / peak);
  }

  // Simplified Sharpe: mean(pnl) / stddev(pnl) * sqrt(n)
  const pnls = closing.map((t) => t.realizedPnl);
  const mean = pnls.reduce((s, v) => s + v, 0) / pnls.length;
  const variance = pnls.reduce((s, v) => s + (v - mean) ** 2, 0) / pnls.length;
  const stddev = Math.sqrt(variance);
  const sharpe = stddev > 0 ? (mean / stddev) * Math.sqrt(pnls.length) : 0;

  // Avg trade duration
  const durations = [];
  const opens = [];
  for (const t of trades) {
    const type = (t.executionType ?? "").toUpperCase();
    if (type === "OPEN" || type === "BUY") opens.push(t.timestamp);
    else if ((type === "CLOSE" || type === "SELL") && opens.length) {
      durations.push(t.timestamp - opens.shift());
    }
  }
  const avgDurationMs = durations.length
    ? durations.reduce((a, b) => a + b, 0) / durations.length
    : null;

  return { winRate, profitFactor, maxDD, sharpe, avgDurationMs, winCount: wins.length, totalCount: closing.length };
}

function fmtDuration(ms) {
  if (ms == null) return "—";
  const h = Math.round(ms / 3600000);
  if (h < 24) return `${h}h`;
  return `${(h / 24).toFixed(1)}d`;
}

// ── Component ─────────────────────────────────────────────────────────────

export default function StatsBar({ result, summary }) {
  const trades = useMemo(() => Array.isArray(result?.trades) ? result.trades : [], [result]);
  const stats = useMemo(() => computeStats(trades), [trades]);

  const animReturn = useCountUp((summary?.totalReturn ?? 0) * 100, result);
  const animBalance = useCountUp(summary?.finalBalance ?? 0, result);

  const totalReturn = summary?.totalReturn ?? 0;

  return (
    <div className="bt-stats-bar">
      {/* Total Return */}
      <div className="bt-stats-bar__cell">
        <span className="bt-stats-bar__label">Total Return</span>
        <span className={`bt-stats-bar__val ${totalReturn >= 0 ? "bt-stats-bar__val--pos" : "bt-stats-bar__val--neg"}`}>
          {animReturn >= 0 ? "+" : ""}{animReturn.toFixed(2)}%
        </span>
        <span className="bt-stats-bar__sub">{formatPrice(animBalance)}</span>
      </div>

      {/* Win Rate */}
      <div className="bt-stats-bar__cell">
        <span className="bt-stats-bar__label">Win Rate</span>
        <span className="bt-stats-bar__val">
          {stats ? `${(stats.winRate * 100).toFixed(1)}%` : "—"}
        </span>
        <span className="bt-stats-bar__sub">
          {stats ? `${stats.winCount}/${stats.totalCount} trades` : `${summary?.tradesCount ?? 0} trades`}
        </span>
      </div>

      {/* Max Drawdown */}
      <div className="bt-stats-bar__cell">
        <span className="bt-stats-bar__label">Max Drawdown</span>
        <span className={`bt-stats-bar__val ${stats?.maxDD > 0 ? "bt-stats-bar__val--neg" : ""}`}>
          {stats
            ? stats.maxDD > 0
              ? `-${(stats.maxDD * 100).toFixed(1)}%`
              : "0.0%"
            : "—"}
        </span>
      </div>

      {/* Sharpe Ratio */}
      <div className="bt-stats-bar__cell">
        <span className="bt-stats-bar__label">Sharpe (est.)</span>
        <span className="bt-stats-bar__val">
          {stats ? stats.sharpe.toFixed(2) : "—"}
        </span>
      </div>

      {/* Profit Factor */}
      <div className="bt-stats-bar__cell">
        <span className="bt-stats-bar__label">Profit Factor</span>
        <span className={`bt-stats-bar__val ${stats ? (stats.profitFactor >= 1 ? "bt-stats-bar__val--pos" : "bt-stats-bar__val--neg") : ""}`}>
          {stats ? (stats.profitFactor >= 99 ? "∞" : `${stats.profitFactor.toFixed(2)}×`) : "—"}
        </span>
      </div>

      {/* Avg Duration */}
      <div className="bt-stats-bar__cell">
        <span className="bt-stats-bar__label">Avg Duration</span>
        <span className="bt-stats-bar__val">
          {stats ? fmtDuration(stats.avgDurationMs) : "—"}
        </span>
      </div>
    </div>
  );
}
