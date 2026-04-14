import { Component } from 'react';
import { useApi } from '../hooks/useApi';
import './StrategyLeaderboard.css';

// ── source badge colours ────────────────────────────────────────────
const SOURCE_META = {
  BUILTIN:  { label: 'Built-in', cls: 'badge--builtin' },
  USER:     { label: 'User',     cls: 'badge--user'    },
  LLM:      { label: 'LLM',      cls: 'badge--llm'     },
  EVOLVED:  { label: 'Evolved',  cls: 'badge--evolved' },
};

function SourceBadge({ source }) {
  const meta = SOURCE_META[source] ?? { label: source, cls: '' };
  return <span className={`sl-badge ${meta.cls}`}>{meta.label}</span>;
}

function StatusDot({ status }) {
  return (
    <span
      className={`sl-status-dot ${status === 'ACTIVE' ? 'sl-status-dot--active' : 'sl-status-dot--eliminated'}`}
      title={status}
    />
  );
}

function SkeletonRow({ rank }) {
  return (
    <tr className="sl-row sl-row--skeleton" aria-hidden="true">
      <td className="sl-cell sl-cell--rank">{rank}</td>
      <td className="sl-cell sl-cell--name"><span className="sl-skel" style={{ width: '140px' }} /></td>
      <td className="sl-cell"><span className="sl-skel" style={{ width: '56px' }} /></td>
      <td className="sl-cell sl-cell--num"><span className="sl-skel" style={{ width: '40px' }} /></td>
      <td className="sl-cell sl-cell--num"><span className="sl-skel" style={{ width: '44px' }} /></td>
      <td className="sl-cell sl-cell--num"><span className="sl-skel" style={{ width: '60px' }} /></td>
      <td className="sl-cell sl-cell--status"><span className="sl-skel" style={{ width: '24px' }} /></td>
    </tr>
  );
}

function fmt(n, decimals = 2) {
  if (n == null) return '—';
  return Number(n).toFixed(decimals);
}

function fmtPnl(n) {
  if (n == null) return '—';
  const sign = n >= 0 ? '+' : '';
  return `${sign}${Number(n).toFixed(2)}`;
}

// ── inner component (no ErrorBoundary) ─────────────────────────────
function LeaderboardInner() {
  const { data, loading, error } = useApi('/api/strategies/leaderboard', 30_000);

  function handleRowClick(strategyId) {
    window.dispatchEvent(new CustomEvent('strategy-selected', { detail: { strategyId } }));
  }

  if (error) {
    return (
      <div className="sl-error">
        <span className="sl-error__icon">⚠</span>
        Leaderboard unavailable
      </div>
    );
  }

  const strategies = data ?? [];

  return (
    <div className="sl-wrap">
      <div className="sl-header">
        <h3 className="sl-title">Strategy Leaderboard</h3>
        <span className="sl-subtitle">Top 10 · refreshes every 30 s</span>
      </div>

      <div className="sl-table-scroll">
        <table className="sl-table">
          <thead>
            <tr>
              <th className="sl-th sl-th--rank">#</th>
              <th className="sl-th sl-th--name">Strategy</th>
              <th className="sl-th">Source</th>
              <th className="sl-th sl-th--num">Sharpe</th>
              <th className="sl-th sl-th--num">Win rate</th>
              <th className="sl-th sl-th--num">Total PnL</th>
              <th className="sl-th sl-th--status">Status</th>
            </tr>
          </thead>
          <tbody>
            {loading && strategies.length === 0
              ? Array.from({ length: 10 }, (_, i) => <SkeletonRow key={i} rank={i + 1} />)
              : strategies.slice(0, 10).map((s, i) => (
                  <tr
                    key={s.id ?? s.strategyId ?? i}
                    className="sl-row sl-row--data"
                    onClick={() => handleRowClick(s.id ?? s.strategyId)}
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && handleRowClick(s.id ?? s.strategyId)}
                    role="button"
                    aria-label={`Select strategy ${s.name}`}
                  >
                    <td className="sl-cell sl-cell--rank">{i + 1}</td>
                    <td className="sl-cell sl-cell--name">{s.name}</td>
                    <td className="sl-cell"><SourceBadge source={s.source} /></td>
                    <td className="sl-cell sl-cell--num">{fmt(s.sharpeRatio ?? s.sharpe)}</td>
                    <td className="sl-cell sl-cell--num">{s.winRate != null ? `${fmt(s.winRate * 100, 1)}%` : '—'}</td>
                    <td className={`sl-cell sl-cell--num ${(s.totalPnl ?? s.pnl) >= 0 ? 'sl-pnl--pos' : 'sl-pnl--neg'}`}>
                      {fmtPnl(s.totalPnl ?? s.pnl)}
                    </td>
                    <td className="sl-cell sl-cell--status"><StatusDot status={s.status} /></td>
                  </tr>
                ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ── ErrorBoundary wrapper ───────────────────────────────────────────
class LeaderboardBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { crashed: false };
  }
  static getDerivedStateFromError() {
    return { crashed: true };
  }
  render() {
    if (this.state.crashed) {
      return (
        <div className="sl-error">
          <span className="sl-error__icon">⚠</span>
          Leaderboard unavailable
        </div>
      );
    }
    return <LeaderboardInner />;
  }
}

export default function StrategyLeaderboard() {
  return <LeaderboardBoundary />;
}
