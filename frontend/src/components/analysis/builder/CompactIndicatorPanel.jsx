import { useEffect, useMemo, useState } from "react";
import Panel from "../../shared/Panel";
import FieldTooltip from "./FieldTooltip";
import { getIndicatorVisibleFields, INDICATOR_LIBRARY } from "../../../utils/strategyDsl";

const LINE_WIDTH_OPTIONS = [1, 2, 3, 4];

const CATEGORY_ORDER = ["TREND", "MOMENTUM", "VOLATILITY", "HYBRID", "VOLUME", "UTILITY"];
const CATEGORY_LABELS = {
  TREND: "Trend",
  MOMENTUM: "Momentum",
  VOLATILITY: "Volatility",
  HYBRID: "Hybrid",
  VOLUME: "Volume",
  UTILITY: "Utility"
};

function getChipLabel(indicator) {
  const def = INDICATOR_LIBRARY[indicator.type];
  if (!def) return indicator.id;
  const shortLabel = def.shortLabel ?? def.label;
  const firstNumeric = def.params?.find((p) => p.type === "number");
  if (firstNumeric && indicator.params?.[firstNumeric.name] != null) {
    return `${shortLabel}(${indicator.params[firstNumeric.name]})`;
  }
  return shortLabel;
}

// ── Config editor shown when a chip or item is clicked ──────────────────────

function IndicatorConfigEditor({ indicator, inputReferences, onClose, onUpdate }) {
  const definition = INDICATOR_LIBRARY[indicator.type];
  const fields = getIndicatorVisibleFields(indicator);
  const availableInputRefs = (inputReferences ?? []).filter(
    (opt) => opt.value !== indicator.id && !opt.value.startsWith(`${indicator.id}.`)
  );

  return (
    <div className="tv-indicator-editor">
      <div className="tv-indicator-editor__header">
        <button className="tv-chip-button" type="button" onClick={onClose}>
          ← Back
        </button>
        <div>
          <span className="tv-panel__eyebrow">{CATEGORY_LABELS[definition?.category] ?? definition?.category ?? "Indicator"}</span>
          <strong className="tv-indicator-editor__title">{definition?.label ?? indicator.type}</strong>
        </div>
      </div>

      <div className="tv-indicator-editor__fields">
        {/* Source input */}
        {definition?.supportsInput ? (
          typeof indicator.input === "string" || !indicator.input ? (
            <label className="tv-indicator-card__field">
              <span>
                Input Source
                <FieldTooltip text="Select a price stream or another indicator output as the source." />
              </span>
              <select
                className="tv-input tv-input--compact"
                value={indicator.input ?? definition.defaultInput ?? "close"}
                onChange={(e) => onUpdate({ ...indicator, input: e.target.value })}
              >
                {availableInputRefs.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </label>
          ) : (
            <div className="tv-indicator-card__field">
              <span>Input Source</span>
              <div className="tv-banner">Nested input — edit in JSON mode.</div>
            </div>
          )
        ) : null}

        {/* Numeric / select / reference params */}
        {fields.map((field) => (
          <label className="tv-indicator-card__field" key={`${indicator.id}-${field.name}`}>
            <span>
              {field.label}
              <FieldTooltip text={field.tooltip} />
            </span>
            {field.type === "select" ? (
              <select
                className="tv-input tv-input--compact"
                value={indicator.params[field.name]}
                onChange={(e) => onUpdate({ ...indicator, params: { ...indicator.params, [field.name]: e.target.value } })}
              >
                {field.options.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            ) : field.type === "reference" ? (
              <select
                className="tv-input tv-input--compact"
                value={indicator.params[field.name] ?? ""}
                onChange={(e) => onUpdate({ ...indicator, params: { ...indicator.params, [field.name]: e.target.value } })}
              >
                <option value="">Select reference</option>
                {availableInputRefs.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            ) : (
              <input
                className="tv-input tv-input--compact"
                type="number"
                min={field.min}
                max={field.max}
                step={field.step}
                value={indicator.params[field.name]}
                onChange={(e) => onUpdate({ ...indicator, params: { ...indicator.params, [field.name]: Number(e.target.value) } })}
              />
            )}
          </label>
        ))}

        {/* Chart style (overlay indicators only) */}
        {definition?.chartOverlay ? (
          <>
            <label className="tv-indicator-card__field">
              <span>
                Line Color
                <FieldTooltip text="Color of the indicator overlay on the chart." />
              </span>
              <input
                className="tv-color-input"
                type="color"
                value={indicator.style?.color ?? "#78d2ff"}
                onChange={(e) => onUpdate({ ...indicator, style: { ...indicator.style, color: e.target.value } })}
              />
            </label>
            <label className="tv-indicator-card__field">
              <span>
                Thickness
                <FieldTooltip text="Line width on the chart." />
              </span>
              <select
                className="tv-input tv-input--compact"
                value={indicator.style?.lineWidth ?? 2}
                onChange={(e) => onUpdate({ ...indicator, style: { ...indicator.style, lineWidth: Number(e.target.value) } })}
              >
                {LINE_WIDTH_OPTIONS.map((w) => (
                  <option key={w} value={w}>{w}px</option>
                ))}
              </select>
            </label>
            {indicator.type === "BOLLINGER" && (
              <label className="tv-indicator-card__field">
                <span>
                  Band Color
                  <FieldTooltip text="Color of the upper and lower Bollinger bands." />
                </span>
                <input
                  className="tv-color-input"
                  type="color"
                  value={indicator.style?.bandColor?.startsWith?.("#") ? indicator.style.bandColor : (indicator.style?.color ?? "#f7c75f")}
                  onChange={(e) => onUpdate({ ...indicator, style: { ...indicator.style, bandColor: e.target.value } })}
                />
              </label>
            )}
          </>
        ) : (
          <div className="tv-indicator-card__field">
            <div className="tv-banner">Oscillator — appears in the sub-pane below the main chart.</div>
          </div>
        )}

        {/* Reference identifier */}
        <label className="tv-indicator-card__field">
          <span>
            Identifier
            <FieldTooltip text="The reference name used in condition rules. Must be unique." />
          </span>
          <input
            className="tv-input tv-input--compact"
            value={indicator.id}
            onChange={(e) => onUpdate({ ...indicator, id: e.target.value.replace(/\s+/g, "") })}
          />
        </label>
      </div>
    </div>
  );
}

// ── Main panel ───────────────────────────────────────────────────────────────

export default function CompactIndicatorPanel({
  indicatorOptions,
  inputReferences,
  indicators,
  onAddIndicator,
  onRemoveIndicator,
  onUpdateIndicator
}) {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [collapsedCategories, setCollapsedCategories] = useState(new Set());
  const [editingId, setEditingId] = useState(null);
  const [pendingOpenType, setPendingOpenType] = useState(null);

  // 200ms search debounce
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 200);
    return () => clearTimeout(timer);
  }, [search]);

  // After add: open editor for the newly created indicator
  useEffect(() => {
    if (!pendingOpenType) return;
    const matches = indicators.filter((i) => i.type === pendingOpenType);
    if (matches.length > 0) {
      setEditingId(matches[matches.length - 1].id);
      setPendingOpenType(null);
    }
  }, [indicators, pendingOpenType]);

  // Build category groups filtered by search
  const categoryGroups = useMemo(() => {
    const lower = debouncedSearch.toLowerCase();
    const filtered = debouncedSearch
      ? indicatorOptions.filter((opt) => {
        const def = INDICATOR_LIBRARY[opt.value];
        return (
          opt.label.toLowerCase().includes(lower) ||
          (def?.shortLabel ?? "").toLowerCase().includes(lower) ||
          (def?.description ?? "").toLowerCase().includes(lower)
        );
      })
      : indicatorOptions;

    const groups = new Map();
    filtered.forEach((opt) => {
      const cat = INDICATOR_LIBRARY[opt.value]?.category ?? "UTILITY";
      if (!groups.has(cat)) groups.set(cat, []);
      groups.get(cat).push(opt);
    });

    const ordered = CATEGORY_ORDER.filter((c) => groups.has(c));
    filtered.forEach((opt) => {
      const cat = INDICATOR_LIBRARY[opt.value]?.category ?? "UTILITY";
      if (!CATEGORY_ORDER.includes(cat) && !ordered.includes(cat)) ordered.push(cat);
    });

    return ordered.map((cat) => ({
      category: cat,
      label: CATEGORY_LABELS[cat] ?? cat,
      options: groups.get(cat) ?? []
    }));
  }, [indicatorOptions, debouncedSearch]);

  const activeTypeCount = useMemo(() => {
    const counts = new Map();
    indicators.forEach((i) => counts.set(i.type, (counts.get(i.type) ?? 0) + 1));
    return counts;
  }, [indicators]);

  const editingIndicator = editingId ? indicators.find((i) => i.id === editingId) : null;

  function toggleCategory(cat) {
    setCollapsedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(cat)) next.delete(cat);
      else next.add(cat);
      return next;
    });
  }

  function handleItemClick(type) {
    onAddIndicator(type);
    setPendingOpenType(type);
  }

  function handleRemove(id) {
    if (editingId === id) setEditingId(null);
    onRemoveIndicator(id);
  }

  return (
    <Panel className="tv-panel tv-panel--left">
      <div className="tv-panel__header">
        <div>
          <span className="tv-panel__eyebrow">Indicators</span>
          <h3>Signal Inputs</h3>
          <p>
            {editingIndicator
              ? "Adjust parameters — chart updates live."
              : "Browse and add indicators. Click a chip to configure."}
          </p>
        </div>
      </div>

      {/* Active indicator chips */}
      {indicators.length > 0 && (
        <div className="tv-indicator-chips">
          {indicators.map((ind) => (
            <div key={ind.id} className={`tv-indicator-chip ${editingId === ind.id ? "tv-indicator-chip--editing" : ""}`}>
              <button
                className="tv-indicator-chip__label"
                type="button"
                title="Click to configure"
                onClick={() => setEditingId(editingId === ind.id ? null : ind.id)}
              >
                {getChipLabel(ind)}
              </button>
              <button
                className="tv-indicator-chip__remove"
                type="button"
                aria-label={`Remove ${ind.id}`}
                onClick={() => handleRemove(ind.id)}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {editingIndicator ? (
        <IndicatorConfigEditor
          indicator={editingIndicator}
          inputReferences={inputReferences}
          onClose={() => setEditingId(null)}
          onUpdate={(next) => onUpdateIndicator(editingIndicator.id, next)}
        />
      ) : (
        <>
          {/* Search bar */}
          <input
            className="tv-input tv-indicator-search-input"
            type="search"
            placeholder="Search indicators..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          {/* Category groups */}
          <div className="tv-indicator-categories">
            {categoryGroups.length === 0 && (
              <div className="tv-banner">No results for "{debouncedSearch}".</div>
            )}
            {categoryGroups.map(({ category, label, options }) => {
              const isCollapsed = collapsedCategories.has(category);
              return (
                <div key={category} className="tv-indicator-category">
                  <button
                    className="tv-indicator-category__header"
                    type="button"
                    onClick={() => toggleCategory(category)}
                  >
                    <span>{label}</span>
                    <span className="tv-indicator-category__count">{options.length}</span>
                    <span className="tv-indicator-category__chevron">{isCollapsed ? "▸" : "▾"}</span>
                  </button>

                  {!isCollapsed && (
                    <div className="tv-indicator-category__items">
                      {options.map((opt) => {
                        const def = INDICATOR_LIBRARY[opt.value];
                        const activeCount = activeTypeCount.get(opt.value) ?? 0;
                        return (
                          <button
                            key={opt.value}
                            className={`tv-indicator-item ${activeCount > 0 ? "tv-indicator-item--active" : ""}`}
                            type="button"
                            onClick={() => handleItemClick(opt.value)}
                          >
                            <div className="tv-indicator-item__main">
                              <span className="tv-indicator-item__name">
                                {def?.shortLabel ?? opt.label}
                              </span>
                              {activeCount > 0 && (
                                <span className="tv-indicator-item__badge">
                                  {activeCount > 1 ? `×${activeCount}` : "Active"}
                                </span>
                              )}
                            </div>
                            <span className="tv-indicator-item__desc">
                              {def?.description ?? opt.label}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}
    </Panel>
  );
}
