import { useState, Component } from 'react';
import Editor from '@monaco-editor/react';
import { useApi } from '../../hooks/useApi';
import { apiClient } from '../../services/apiClient';
import './AiPanel.css';

// ── helpers ──────────────────────────────────────────────────────────
function fmt(n, d = 2) {
  if (n == null) return '—';
  return Number(n).toFixed(d);
}
function fmtPct(n) {
  if (n == null) return '—';
  return `${fmt(n * 100, 1)}%`;
}

// ── shared sub-components ─────────────────────────────────────────────
function AiMetricCard({ label, value, colorClass }) {
  return (
    <div className="ai-metric">
      <span className="ai-metric__label">{label}</span>
      <span className={`ai-metric__value ${colorClass ?? ''}`}>{value}</span>
    </div>
  );
}

function VerdictBadge({ verdict }) {
  const cls = {
    STRONG:     'ai-verdict--strong',
    ACCEPTABLE: 'ai-verdict--acceptable',
    POOR:       'ai-verdict--poor',
  }[verdict] ?? '';
  return <span className={`ai-verdict ${cls}`}>{verdict ?? '—'}</span>;
}

function Spinner() {
  return <span className="ai-spinner" aria-label="Loading" />;
}

// ═══════════════════════════════════════════════════════════════════════
// Section 1 — Generate strategy
// ═══════════════════════════════════════════════════════════════════════
function GenerateSection() {
  const [objective, setObjective] = useState('');
  const [symbol, setSymbol] = useState('BTCUSDT');
  const [interval, setInterval] = useState('1h');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);   // generated DSL object
  const [error, setError] = useState(null);
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState(null);
  const [addedMsg, setAddedMsg] = useState(null);

  async function handleGenerate() {
    if (!objective.trim()) return;
    setLoading(true);
    setError(null);
    setResult(null);
    setAddedMsg(null);
    try {
      const res = await apiClient.post('/api/ai/generate', {
        objective: objective.trim(),
        symbol,
        interval,
      });
      setResult(res?.dsl ?? res);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleAddToLibrary() {
    if (!result) return;
    setAddLoading(true);
    setAddError(null);
    try {
      await apiClient.post('/api/strategies', {
        ...(typeof result === 'object' ? result : {}),
        source: 'LLM',
      });
      setAddedMsg('Strategy added to library.');
    } catch (err) {
      setAddError(err.message);
    } finally {
      setAddLoading(false);
    }
  }

  const dslString = result
    ? (typeof result === 'string' ? result : JSON.stringify(result, null, 2))
    : '';

  return (
    <section className="ai-section">
      <h2 className="ai-section__title">Generate Strategy</h2>

      <label className="ai-label" htmlFor="ai-objective">
        Describe what you want the strategy to do
      </label>
      <textarea
        id="ai-objective"
        className="ai-textarea"
        rows={4}
        placeholder="e.g. Buy when RSI is oversold and MACD crosses above signal, sell when RSI is overbought."
        value={objective}
        onChange={(e) => setObjective(e.target.value)}
      />

      <div className="ai-controls-row">
        <div className="ai-select-group">
          <label className="ai-label" htmlFor="ai-symbol">Symbol</label>
          <select
            id="ai-symbol"
            className="ai-select"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
          >
            <option value="BTCUSDT">BTCUSDT</option>
            <option value="ETHUSDT">ETHUSDT</option>
            <option value="SOLUSDT">SOLUSDT</option>
          </select>
        </div>

        <div className="ai-select-group">
          <label className="ai-label" htmlFor="ai-interval">Interval</label>
          <select
            id="ai-interval"
            className="ai-select"
            value={interval}
            onChange={(e) => setInterval(e.target.value)}
          >
            <option value="1m">1m</option>
            <option value="5m">5m</option>
            <option value="15m">15m</option>
            <option value="1h">1h</option>
          </select>
        </div>

        <button
          type="button"
          className="ai-btn ai-btn--primary"
          onClick={handleGenerate}
          disabled={loading || !objective.trim()}
        >
          {loading ? <><Spinner /> Generating…</> : 'Generate'}
        </button>
      </div>

      {error && (
        <p className="ai-inline-error" role="alert">{error}</p>
      )}

      {result && (
        <>
          <div className="ai-editor-label">Generated DSL</div>
          <div className="ai-editor-wrap">
            <Editor
              height="300px"
              defaultLanguage="json"
              value={dslString}
              theme="vs-dark"
              options={{
                readOnly: true,
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                fontSize: 13,
              }}
            />
          </div>

          <div className="ai-add-row">
            <button
              type="button"
              className="ai-btn ai-btn--secondary"
              onClick={handleAddToLibrary}
              disabled={addLoading || !!addedMsg}
            >
              {addLoading ? <><Spinner /> Adding…</> : 'Add to Library'}
            </button>
            {addedMsg && <span className="ai-success-msg">{addedMsg}</span>}
            {addError && <span className="ai-inline-error">{addError}</span>}
          </div>
        </>
      )}
    </section>
  );
}

// ═══════════════════════════════════════════════════════════════════════
// Section 2 — Memory stats
// ═══════════════════════════════════════════════════════════════════════
function MemorySection() {
  const { data: stats, loading: statsLoading, error: statsError } =
    useApi('/api/ai/memory/stats');
  const { data: relevant, loading: relLoading, error: relError } =
    useApi('/api/ai/memory/relevant?symbol=BTCUSDT&interval=1h');

  const memories = relevant ?? [];

  return (
    <section className="ai-section">
      <h2 className="ai-section__title">AI Memory</h2>

      {statsError && <p className="ai-inline-error">{statsError}</p>}

      {!statsError && (
        <div className="ai-metrics-row">
          <AiMetricCard
            label="Total Memories"
            value={statsLoading ? '…' : (stats?.totalMemories ?? stats?.total ?? '—')}
          />
          <AiMetricCard
            label="Avg Sharpe"
            value={statsLoading ? '…' : fmt(stats?.avgSharpe)}
          />
          <AiMetricCard
            label="Strong"
            value={statsLoading ? '…' : (stats?.strongCount ?? stats?.strong ?? '—')}
            colorClass="ai-pos"
          />
          <AiMetricCard
            label="Acceptable"
            value={statsLoading ? '…' : (stats?.acceptableCount ?? stats?.acceptable ?? '—')}
            colorClass="ai-warn"
          />
          <AiMetricCard
            label="Poor"
            value={statsLoading ? '…' : (stats?.poorCount ?? stats?.poor ?? '—')}
            colorClass="ai-neg"
          />
        </div>
      )}

      <h3 className="ai-subsection-title">
        Relevant memories — BTCUSDT · 1h
      </h3>

      {relError && <p className="ai-inline-error">{relError}</p>}

      {!relError && (
        <div className="ai-table-scroll">
          <table className="ai-table">
            <thead>
              <tr>
                <th className="ai-th">Strategy</th>
                <th className="ai-th">Verdict</th>
                <th className="ai-th ai-th--num">Sharpe</th>
                <th className="ai-th ai-th--num">Win rate</th>
                <th className="ai-th">Lesson</th>
              </tr>
            </thead>
            <tbody>
              {relLoading && memories.length === 0 ? (
                Array.from({ length: 4 }, (_, i) => (
                  <tr key={i} className="ai-row ai-row--skeleton">
                    {Array.from({ length: 5 }, (__, j) => (
                      <td key={j} className="ai-cell">
                        <span className="ai-skel" style={{ width: `${60 + j * 20}px` }} />
                      </td>
                    ))}
                  </tr>
                ))
              ) : memories.length === 0 ? (
                <tr><td className="ai-cell ai-cell--empty" colSpan={5}>No memories found</td></tr>
              ) : (
                memories.map((m, i) => (
                  <tr key={m.id ?? i} className="ai-row">
                    <td className="ai-cell">{m.strategyName ?? m.strategy ?? '—'}</td>
                    <td className="ai-cell"><VerdictBadge verdict={m.verdict} /></td>
                    <td className="ai-cell ai-cell--num">{fmt(m.sharpe ?? m.sharpeRatio)}</td>
                    <td className="ai-cell ai-cell--num">{fmtPct(m.winRate)}</td>
                    <td className="ai-cell ai-cell--lesson">{m.lesson ?? m.document ?? '—'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ═══════════════════════════════════════════════════════════════════════
// Section 3 — Learning loop
// ═══════════════════════════════════════════════════════════════════════
function LoopSection() {
  const { data: health, loading: healthLoading, error: healthError } =
    useApi('/api/ai/loop/health', 60_000);
  const { data: historyData, loading: histLoading, error: histError } =
    useApi('/api/ai/loop/history', 60_000);
  const { data: statusData } =
    useApi('/api/ai/loop/status', 10_000);

  const [confirmVisible, setConfirmVisible] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [triggerMsg, setTriggerMsg] = useState(null);
  const [triggerError, setTriggerError] = useState(null);

  const isRunning = statusData?.isRunning ?? false;
  const history = historyData ?? [];

  async function handleTrigger() {
    setTriggering(true);
    setTriggerMsg(null);
    setTriggerError(null);
    setConfirmVisible(false);
    try {
      await apiClient.post('/api/ai/loop/trigger', {});
      setTriggerMsg('Loop triggered. Check history in 2 minutes.');
    } catch (err) {
      setTriggerError(err.message);
    } finally {
      setTriggering(false);
    }
  }

  const statusLabel = health?.status ?? (isRunning ? 'Active' : 'Paused');
  const statusActive = statusLabel === 'Active' || isRunning;

  return (
    <section className="ai-section">
      <h2 className="ai-section__title">Learning Loop</h2>

      {healthError && <p className="ai-inline-error">{healthError}</p>}

      {!healthError && (
        <div className="ai-metrics-row">
          <AiMetricCard
            label="Memory Quality"
            value={healthLoading ? '…' : fmt(health?.memoryQualityScore ?? health?.qualityScore)}
          />
          <AiMetricCard
            label="Generation Success"
            value={healthLoading ? '…' : (health?.generationSuccessRate != null ? fmtPct(health.generationSuccessRate) : '—')}
          />
          <AiMetricCard
            label="Memory Count"
            value={healthLoading ? '…' : (health?.memoryCount ?? health?.count ?? '—')}
          />
          <AiMetricCard
            label="Status"
            value={healthLoading ? '…' : statusLabel}
            colorClass={statusActive ? 'ai-pos' : 'ai-muted'}
          />
        </div>
      )}

      <div className="ai-loop-controls">
        {!confirmVisible ? (
          <button
            type="button"
            className="ai-btn ai-btn--primary"
            onClick={() => setConfirmVisible(true)}
            disabled={isRunning || triggering}
          >
            {isRunning ? <><Spinner /> Loop running…</> : 'Trigger loop now'}
          </button>
        ) : (
          <div className="ai-confirm-row">
            <span className="ai-confirm-msg">
              This will run the full learning loop (~2 min). Continue?
            </span>
            <button
              type="button"
              className="ai-btn ai-btn--primary"
              onClick={handleTrigger}
              disabled={triggering}
            >
              {triggering ? <><Spinner /> Triggering…</> : 'Confirm'}
            </button>
            <button
              type="button"
              className="ai-btn ai-btn--secondary"
              onClick={() => setConfirmVisible(false)}
              disabled={triggering}
            >
              Cancel
            </button>
          </div>
        )}
        {triggerMsg && <span className="ai-success-msg">{triggerMsg}</span>}
        {triggerError && <span className="ai-inline-error">{triggerError}</span>}
      </div>

      <h3 className="ai-subsection-title">Loop history</h3>

      {histError && <p className="ai-inline-error">{histError}</p>}

      {!histError && (
        <div className="ai-table-scroll">
          <table className="ai-table">
            <thead>
              <tr>
                <th className="ai-th">Date</th>
                <th className="ai-th ai-th--num">Memories stored</th>
                <th className="ai-th ai-th--num">Generated</th>
                <th className="ai-th ai-th--num">Promoted</th>
                <th className="ai-th ai-th--num">Pruned</th>
                <th className="ai-th ai-th--num">Avg Sharpe</th>
              </tr>
            </thead>
            <tbody>
              {histLoading && history.length === 0 ? (
                Array.from({ length: 4 }, (_, i) => (
                  <tr key={i} className="ai-row ai-row--skeleton">
                    {Array.from({ length: 6 }, (__, j) => (
                      <td key={j} className="ai-cell">
                        <span className="ai-skel" style={{ width: `${50 + j * 12}px` }} />
                      </td>
                    ))}
                  </tr>
                ))
              ) : history.length === 0 ? (
                <tr><td className="ai-cell ai-cell--empty" colSpan={6}>No history yet</td></tr>
              ) : (
                history.map((row, i) => (
                  <tr key={row.id ?? i} className="ai-row">
                    <td className="ai-cell ai-cell--mono">
                      {row.date ? new Date(row.date).toLocaleDateString() : '—'}
                    </td>
                    <td className="ai-cell ai-cell--num">{row.memoriesStored ?? row.stored ?? '—'}</td>
                    <td className="ai-cell ai-cell--num">{row.generated ?? '—'}</td>
                    <td className="ai-cell ai-cell--num ai-pos">{row.promoted ?? '—'}</td>
                    <td className="ai-cell ai-cell--num ai-neg">{row.pruned ?? '—'}</td>
                    <td className="ai-cell ai-cell--num">{fmt(row.avgSharpe)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ═══════════════════════════════════════════════════════════════════════
// ErrorBoundary + page root
// ═══════════════════════════════════════════════════════════════════════
class AiBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { crashed: false, message: '' };
  }
  static getDerivedStateFromError(err) {
    return { crashed: true, message: err?.message ?? '' };
  }
  render() {
    if (this.state.crashed) {
      return (
        <div className="ai-crash">
          <p className="ai-crash__title">AI Panel failed to load</p>
          <p className="ai-crash__msg">{this.state.message}</p>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function AiPanel() {
  return (
    <AiBoundary>
      <div className="ai-page">
        <div className="ai-page__header">
          <h1 className="ai-page__title">AI Strategy Studio</h1>
          <span className="ai-page__subtitle">
            LLM-assisted generation · memory-driven learning
          </span>
        </div>
        <GenerateSection />
        <MemorySection />
        <LoopSection />
      </div>
    </AiBoundary>
  );
}
