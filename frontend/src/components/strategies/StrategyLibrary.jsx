import { useState, useCallback } from 'react';
import { Component } from 'react';
import Editor from '@monaco-editor/react';
import { useApi } from '../../hooks/useApi';
import { apiClient, ApiError } from '../../services/apiClient';
import './StrategyLibrary.css';

// ── constants ────────────────────────────────────────────────────────
const TABS = ['ALL', 'BUILTIN', 'USER', 'LLM', 'EVOLVED'];

const SOURCE_META = {
  BUILTIN: { label: 'Built-in', cls: 'lib-badge--builtin' },
  USER:    { label: 'User',     cls: 'lib-badge--user'    },
  LLM:     { label: 'LLM',     cls: 'lib-badge--llm'     },
  EVOLVED: { label: 'Evolved', cls: 'lib-badge--evolved'  },
};

const DSL_TEMPLATE = JSON.stringify(
  {
    name: 'New Strategy',
    symbol: 'BTCUSDT',
    interval: '1h',
    indicators: [
      { id: 'ema_fast', type: 'EMA', params: { period: 12, input: 'close' } },
      { id: 'ema_slow', type: 'EMA', params: { period: 26, input: 'close' } },
    ],
    entryRules: {
      operator: 'AND',
      rules: [{ left: 'ema_fast', operator: 'CROSS_ABOVE', right: 'ema_slow' }],
    },
    exitRules: {
      operator: 'OR',
      rules: [{ left: 'ema_fast', operator: 'CROSS_BELOW', right: 'ema_slow' }],
    },
    params: { stopLoss: 0.02, takeProfit: 0.04 },
  },
  null,
  2
);

// ── helpers ──────────────────────────────────────────────────────────
function fmt(n, d = 2) {
  if (n == null) return '—';
  return Number(n).toFixed(d);
}
function fmtPct(n) {
  if (n == null) return '—';
  return `${fmt(n * 100, 1)}%`;
}
function fmtPnl(n) {
  if (n == null) return '—';
  return `${n >= 0 ? '+' : ''}${fmt(n)}`;
}

// ── sub-components ───────────────────────────────────────────────────
function SourceBadge({ source }) {
  const meta = SOURCE_META[source] ?? { label: source, cls: '' };
  return <span className={`lib-badge ${meta.cls}`}>{meta.label}</span>;
}

function MetricCard({ label, value, colorClass }) {
  return (
    <div className="lib-metric">
      <span className="lib-metric__label">{label}</span>
      <span className={`lib-metric__value ${colorClass ?? ''}`}>{value}</span>
    </div>
  );
}

function SkeletonItem() {
  return (
    <div className="lib-list-item lib-list-item--skeleton" aria-hidden="true">
      <div className="lib-list-item__row1">
        <span className="lib-skel" style={{ width: '130px', height: '13px' }} />
        <span className="lib-skel" style={{ width: '48px', height: '13px' }} />
      </div>
      <div className="lib-list-item__row2">
        <span className="lib-skel" style={{ width: '60px', height: '11px' }} />
        <span className="lib-skel" style={{ width: '52px', height: '11px' }} />
      </div>
    </div>
  );
}

// ── left panel ───────────────────────────────────────────────────────
function StrategyList({ activeTab, onTabChange, onSelect, selectedId, onAddNew }) {
  const listUrl = activeTab === 'ALL'
    ? '/api/strategies'
    : `/api/strategies?source=${activeTab}`;
  const { data, loading, error } = useApi(listUrl);
  const strategies = data ?? [];

  return (
    <div className="lib-left">
      <div className="lib-left__header">
        <h2 className="lib-panel-title">Strategy Library</h2>
        <button className="lib-add-btn" type="button" onClick={onAddNew}>
          + Add Strategy
        </button>
      </div>

      <div className="lib-tabs" role="tablist" aria-label="Source filter">
        {TABS.map((tab) => (
          <button
            key={tab}
            role="tab"
            aria-selected={activeTab === tab}
            className={`lib-tab ${activeTab === tab ? 'lib-tab--active' : ''}`}
            type="button"
            onClick={() => onTabChange(tab)}
          >
            {tab === 'ALL' ? 'All' : SOURCE_META[tab]?.label ?? tab}
          </button>
        ))}
      </div>

      <div className="lib-list" role="tabpanel">
        {loading && strategies.length === 0 && (
          Array.from({ length: 6 }, (_, i) => <SkeletonItem key={i} />)
        )}

        {error && (
          <p className="lib-list__error">Could not load strategies</p>
        )}

        {!loading && !error && strategies.length === 0 && (
          <p className="lib-list__empty">No strategies found</p>
        )}

        {strategies.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`lib-list-item ${selectedId === s.id ? 'lib-list-item--active' : ''}`}
            onClick={() => onSelect(s)}
          >
            <div className="lib-list-item__row1">
              <span className="lib-list-item__name">{s.name}</span>
              <SourceBadge source={s.source} />
            </div>
            <div className="lib-list-item__row2">
              <span className="lib-list-item__stat">
                <span className="lib-list-item__stat-label">Sharpe</span>
                <span className="lib-list-item__stat-value">{fmt(s.sharpeRatio ?? s.sharpe)}</span>
              </span>
              <span className="lib-list-item__stat">
                <span className="lib-list-item__stat-label">Win</span>
                <span className="lib-list-item__stat-value">{fmtPct(s.winRate)}</span>
              </span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

// ── right panel – view mode ──────────────────────────────────────────
function ViewPanel({ strategy, onAddToLibrary }) {
  const [backtestResult, setBacktestResult] = useState(null);
  const [backtesting, setBacktesting] = useState(false);
  const [backtestError, setBacktestError] = useState(null);
  const [addedToLibrary, setAddedToLibrary] = useState(false);
  const [addError, setAddError] = useState(null);
  const [adding, setAdding] = useState(false);

  const metrics = strategy.metrics ?? strategy.latestBacktest ?? backtestResult;
  const dsl = strategy.dsl ?? strategy.config ?? strategy;

  async function handleRunBacktest() {
    setBacktesting(true);
    setBacktestError(null);
    try {
      const result = await apiClient.post('/api/backtest/simulate/dsl', {
        dsl: typeof dsl === 'string' ? JSON.parse(dsl) : dsl,
      });
      setBacktestResult(result);
    } catch (err) {
      setBacktestError(err.message);
    } finally {
      setBacktesting(false);
    }
  }

  async function handleAddToLibrary() {
    setAdding(true);
    setAddError(null);
    try {
      await apiClient.post('/api/strategies', {
        dsl: typeof dsl === 'string' ? JSON.parse(dsl) : dsl,
        name: strategy.name,
        source: 'USER',
      });
      setAddedToLibrary(true);
      onAddToLibrary?.();
    } catch (err) {
      setAddError(err.message);
    } finally {
      setAdding(false);
    }
  }

  const dslString = typeof dsl === 'string' ? dsl : JSON.stringify(dsl, null, 2);
  const showAddButton = strategy.source !== 'USER' && !addedToLibrary;

  return (
    <div className="lib-right">
      <div className="lib-right__header">
        <div>
          <h2 className="lib-panel-title">{strategy.name}</h2>
          <SourceBadge source={strategy.source} />
        </div>
        <div className="lib-right__actions">
          {showAddButton && (
            <button
              type="button"
              className="lib-btn lib-btn--secondary"
              onClick={handleAddToLibrary}
              disabled={adding}
            >
              {adding ? 'Saving…' : 'Add to Library'}
            </button>
          )}
          {addedToLibrary && <span className="lib-saved-badge">Saved</span>}
          <button
            type="button"
            className="lib-btn lib-btn--primary"
            onClick={handleRunBacktest}
            disabled={backtesting}
          >
            {backtesting ? 'Running…' : 'Run Backtest'}
          </button>
        </div>
      </div>

      {addError && <div className="lib-error-banner">{addError}</div>}
      {backtestError && <div className="lib-error-banner">{backtestError}</div>}

      {metrics && (
        <div className="lib-metrics-row">
          <MetricCard
            label="Sharpe"
            value={fmt(metrics.sharpeRatio ?? metrics.sharpe)}
          />
          <MetricCard
            label="Win Rate"
            value={fmtPct(metrics.winRate)}
          />
          <MetricCard
            label="Total PnL"
            value={fmtPnl(metrics.totalPnl ?? metrics.pnl)}
            colorClass={(metrics.totalPnl ?? metrics.pnl) >= 0 ? 'lib-pos' : 'lib-neg'}
          />
          <MetricCard
            label="Max Drawdown"
            value={fmtPct(metrics.maxDrawdown)}
            colorClass="lib-neg"
          />
          <MetricCard
            label="Total Trades"
            value={metrics.totalTrades ?? '—'}
          />
        </div>
      )}

      <div className="lib-editor-label">Strategy DSL</div>
      <div className="lib-editor-wrap">
        <Editor
          height="360px"
          defaultLanguage="json"
          value={dslString}
          theme="vs-dark"
          options={{
            readOnly: true,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            fontSize: 13,
            lineNumbers: 'on',
            folding: true,
          }}
        />
      </div>
    </div>
  );
}

// ── right panel – create mode ────────────────────────────────────────
function CreatePanel({ onSaved }) {
  const [code, setCode] = useState(DSL_TEMPLATE);
  const [validationErrors, setValidationErrors] = useState(null);
  const [validating, setValidating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  async function handleValidate() {
    setValidating(true);
    setValidationErrors(null);
    try {
      let parsed;
      try {
        parsed = JSON.parse(code);
      } catch {
        setValidationErrors(['Invalid JSON: ' + (arguments[0]?.message ?? 'parse error')]);
        return;
      }
      await apiClient.post('/api/strategies?dryRun=true', parsed);
      setValidationErrors([]);
    } catch (err) {
      if (err instanceof ApiError && err.data?.errors) {
        setValidationErrors(err.data.errors);
      } else if (err instanceof ApiError && err.data?.message) {
        setValidationErrors([err.data.message]);
      } else {
        setValidationErrors([err.message]);
      }
    } finally {
      setValidating(false);
    }
  }

  async function handleSave() {
    setSaving(true);
    setSaveError(null);
    try {
      let parsed;
      try {
        parsed = JSON.parse(code);
      } catch (e) {
        setSaveError('Invalid JSON: ' + e.message);
        return;
      }
      await apiClient.post('/api/strategies', parsed);
      onSaved?.();
    } catch (err) {
      setSaveError(err.message);
    } finally {
      setSaving(false);
    }
  }

  const hasErrors = validationErrors && validationErrors.length > 0;
  const isValid = validationErrors !== null && validationErrors.length === 0;

  return (
    <div className="lib-right">
      <div className="lib-right__header">
        <h2 className="lib-panel-title">New Strategy</h2>
        <div className="lib-right__actions">
          <button
            type="button"
            className="lib-btn lib-btn--secondary"
            onClick={handleValidate}
            disabled={validating}
          >
            {validating ? 'Validating…' : 'Validate'}
          </button>
          <button
            type="button"
            className="lib-btn lib-btn--primary"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? 'Saving…' : 'Save to Library'}
          </button>
        </div>
      </div>

      {hasErrors && (
        <div className="lib-error-banner" role="alert">
          <strong>Validation errors</strong>
          <ul className="lib-error-list">
            {validationErrors.map((e, i) => <li key={i}>{e}</li>)}
          </ul>
        </div>
      )}

      {isValid && (
        <div className="lib-success-banner" role="status">
          DSL is valid
        </div>
      )}

      {saveError && (
        <div className="lib-error-banner" role="alert">{saveError}</div>
      )}

      <div className="lib-editor-label">Strategy DSL (JSON)</div>
      <div className="lib-editor-wrap">
        <Editor
          height="420px"
          defaultLanguage="json"
          value={code}
          onChange={(v) => setCode(v ?? '')}
          theme="vs-dark"
          options={{
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            fontSize: 13,
            lineNumbers: 'on',
            formatOnPaste: true,
          }}
        />
      </div>
    </div>
  );
}

// ── empty right-panel placeholder ────────────────────────────────────
function EmptyPanel() {
  return (
    <div className="lib-right lib-right--empty">
      <div className="lib-empty-state">
        <span className="lib-empty-state__icon">↖</span>
        <p className="lib-empty-state__text">Select a strategy or create a new one</p>
      </div>
    </div>
  );
}

// ── ErrorBoundary ────────────────────────────────────────────────────
class PageBoundary extends Component {
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
        <div className="lib-crash">
          <p className="lib-crash__title">Strategy Library failed to load</p>
          <p className="lib-crash__msg">{this.state.message}</p>
        </div>
      );
    }
    return this.props.children;
  }
}

// ── page root ────────────────────────────────────────────────────────
function StrategyLibraryInner() {
  const [activeTab, setActiveTab] = useState('ALL');
  const [panelMode, setPanelMode] = useState(null); // null | 'view' | 'create'
  const [selectedStrategy, setSelectedStrategy] = useState(null);
  const [listKey, setListKey] = useState(0); // bump to force list refetch

  const handleSelect = useCallback((strategy) => {
    setSelectedStrategy(strategy);
    setPanelMode('view');
  }, []);

  const handleAddNew = useCallback(() => {
    setSelectedStrategy(null);
    setPanelMode('create');
  }, []);

  const handleSaved = useCallback(() => {
    setPanelMode(null);
    setSelectedStrategy(null);
    setListKey((k) => k + 1);
  }, []);

  return (
    <div className="lib-page">
      <StrategyList
        key={listKey}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        onSelect={handleSelect}
        selectedId={selectedStrategy?.id}
        onAddNew={handleAddNew}
      />

      {panelMode === 'view' && selectedStrategy && (
        <ViewPanel
          strategy={selectedStrategy}
          onAddToLibrary={() => setListKey((k) => k + 1)}
        />
      )}
      {panelMode === 'create' && (
        <CreatePanel onSaved={handleSaved} />
      )}
      {panelMode === null && <EmptyPanel />}
    </div>
  );
}

export default function StrategyLibrary() {
  return (
    <PageBoundary>
      <StrategyLibraryInner />
    </PageBoundary>
  );
}
