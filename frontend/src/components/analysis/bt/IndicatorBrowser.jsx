import { useEffect, useMemo, useState } from "react";
import { INDICATOR_LIBRARY } from "../../../utils/strategyDsl";

const CATEGORY_ORDER = ["TREND", "MOMENTUM", "VOLATILITY", "HYBRID", "VOLUME", "UTILITY"];
const CATEGORY_LABELS = {
  TREND: "Trend",
  MOMENTUM: "Momentum",
  VOLATILITY: "Volatility",
  HYBRID: "Hybrid",
  VOLUME: "Volume",
  UTILITY: "Utility"
};

const LS_KEY = "bt-ind-browser-collapsed";

function loadCollapsed() {
  try { return new Set(JSON.parse(localStorage.getItem(LS_KEY) ?? "[]")); }
  catch { return new Set(); }
}

function saveCollapsed(set) {
  try { localStorage.setItem(LS_KEY, JSON.stringify([...set])); } catch {}
}

function highlightMatch(text, query) {
  if (!query) return text;
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx === -1) return text;
  return (
    <>
      {text.slice(0, idx)}
      <span className="bt-ind-name-highlight">{text.slice(idx, idx + query.length)}</span>
      {text.slice(idx + query.length)}
    </>
  );
}

export default function IndicatorBrowser({
  indicators,
  onAddIndicator,
  onRemoveIndicator
}) {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [collapsed, setCollapsed] = useState(() => loadCollapsed());

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 150);
    return () => clearTimeout(t);
  }, [search]);

  // Active type→count map
  const activeTypes = useMemo(() => {
    const m = new Map();
    indicators.forEach((ind) => m.set(ind.type, (m.get(ind.type) ?? 0) + 1));
    return m;
  }, [indicators]);

  // Build category groups
  const categoryGroups = useMemo(() => {
    const q = debouncedSearch.toLowerCase();
    const allTypes = Object.keys(INDICATOR_LIBRARY);
    const filtered = q
      ? allTypes.filter((type) => {
          const def = INDICATOR_LIBRARY[type];
          return (
            def.label.toLowerCase().includes(q) ||
            (def.shortLabel ?? "").toLowerCase().includes(q) ||
            (def.description ?? "").toLowerCase().includes(q)
          );
        })
      : allTypes;

    const groups = new Map();
    filtered.forEach((type) => {
      const cat = INDICATOR_LIBRARY[type]?.category ?? "UTILITY";
      if (!groups.has(cat)) groups.set(cat, []);
      groups.get(cat).push(type);
    });

    return CATEGORY_ORDER.filter((c) => groups.has(c)).map((cat) => ({
      cat,
      label: CATEGORY_LABELS[cat] ?? cat,
      types: groups.get(cat) ?? []
    }));
  }, [debouncedSearch]);

  function toggleCollapse(cat) {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(cat)) next.delete(cat);
      else next.add(cat);
      saveCollapsed(next);
      return next;
    });
  }

  function handleToggle(type) {
    const activeCount = activeTypes.get(type) ?? 0;
    if (activeCount > 0) {
      // Remove the most-recently-added instance of this type
      const last = [...indicators].reverse().find((ind) => ind.type === type);
      if (last) onRemoveIndicator(last.id);
    } else {
      onAddIndicator(type);
    }
  }

  return (
    <div className="bt-left">
      <div className="bt-ind-search-wrap">
        <input
          className="bt-ind-search"
          type="search"
          placeholder="Search…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="bt-ind-scroll">
        {categoryGroups.map(({ cat, label, types }) => {
          const isCollapsed = collapsed.has(cat);
          return (
            <div key={cat} className="bt-ind-section">
              <button
                className="bt-ind-section-hdr"
                type="button"
                onClick={() => toggleCollapse(cat)}
              >
                <span>{isCollapsed ? "▸" : "▾"} {label}</span>
                <span className="bt-ind-section-count">{types.length}</span>
              </button>

              {!isCollapsed && types.map((type) => {
                const def = INDICATOR_LIBRARY[type];
                const isActive = (activeTypes.get(type) ?? 0) > 0;
                const badge = def.chartOverlay ? "OL" : "PN";
                const badgeCls = def.chartOverlay ? "bt-ind-badge--ol" : "bt-ind-badge--pn";

                return (
                  <div
                    key={type}
                    className={`bt-ind-row ${isActive ? "bt-ind-row--active" : ""}`}
                    onClick={() => handleToggle(type)}
                  >
                    <span className="bt-ind-row__name">
                      {highlightMatch(def.shortLabel ?? def.label, debouncedSearch)}
                    </span>
                    <span className={`bt-ind-badge ${badgeCls}`}>{badge}</span>
                    <div className="bt-ind-toggle">
                      {isActive && "✓"}
                    </div>
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
