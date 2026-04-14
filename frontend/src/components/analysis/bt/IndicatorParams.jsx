import { useState } from "react";
import { INDICATOR_LIBRARY, getIndicatorVisibleFields } from "../../../utils/strategyDsl";

const INDICATOR_COLORS = [
  "#5badf7", "#f0b90b", "#c084fc", "#0ecb81", "#f6465d",
  "#ff9f43", "#54a0ff", "#00d2d3", "#ff6b6b", "#feca57"
];

function ParamBlock({ indicator, index, inputReferences, onUpdate, onRemove }) {
  const [open, setOpen] = useState(true);
  const def = INDICATOR_LIBRARY[indicator.type];
  const fields = getIndicatorVisibleFields(indicator);
  const color = indicator.style?.color ?? INDICATOR_COLORS[index % INDICATOR_COLORS.length];

  const availableRefs = (inputReferences ?? []).filter(
    (opt) => opt.value !== indicator.id && !opt.value.startsWith(`${indicator.id}.`)
  );

  function upd(patch) {
    onUpdate(indicator.id, { ...indicator, ...patch });
  }

  return (
    <div className="bt-param-block">
      <button
        className="bt-param-block__hdr"
        type="button"
        onClick={() => setOpen((v) => !v)}
      >
        <div className="bt-param-dot" style={{ background: color }} />
        <span className="bt-param-name">
          {def?.shortLabel ?? indicator.type}
          {(() => {
            const p = def?.params?.find((f) => f.type === "number");
            return p && indicator.params?.[p.name] != null ? `(${indicator.params[p.name]})` : "";
          })()}
        </span>

        {/* Color picker */}
        {def?.chartOverlay && (
          <input
            className="bt-param-color-input"
            type="color"
            value={color.startsWith("#") ? color : "#5badf7"}
            onClick={(e) => e.stopPropagation()}
            onChange={(e) => upd({ style: { ...indicator.style, color: e.target.value } })}
            title="Series color"
          />
        )}

        <button
          className="bt-param-block__remove"
          type="button"
          title="Remove indicator"
          onClick={(e) => { e.stopPropagation(); onRemove(indicator.id); }}
        >
          ×
        </button>
        <span className="bt-param-chevron">{open ? "▾" : "▸"}</span>
      </button>

      {open && (
        <div className="bt-param-body">
          {/* Input source */}
          {def?.supportsInput && typeof indicator.input !== "object" && (
            <div className="bt-param-row">
              <span className="bt-param-row-label">Source</span>
              <select
                className="bt-param-select"
                value={indicator.input ?? def.defaultInput ?? "close"}
                onChange={(e) => upd({ input: e.target.value })}
              >
                {availableRefs.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
          )}

          {/* Numeric / select fields */}
          {fields.map((field) => (
            <div key={field.name} className="bt-param-row">
              <span className="bt-param-row-label">{field.label}</span>
              {field.type === "select" ? (
                <select
                  className="bt-param-select"
                  value={indicator.params[field.name]}
                  onChange={(e) => upd({ params: { ...indicator.params, [field.name]: e.target.value } })}
                >
                  {field.options.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              ) : (
                <input
                  className="bt-param-input"
                  type="number"
                  min={field.min}
                  max={field.max}
                  step={field.step}
                  value={indicator.params[field.name]}
                  onChange={(e) => upd({ params: { ...indicator.params, [field.name]: Number(e.target.value) } })}
                />
              )}
            </div>
          ))}

          {/* Identifier */}
          <div className="bt-param-row">
            <span className="bt-param-row-label">ID</span>
            <input
              className="bt-param-input"
              value={indicator.id}
              onChange={(e) => upd({ id: e.target.value.replace(/\s+/g, "") })}
              style={{ width: 80, textAlign: "left" }}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function AdvancedSection({
  executionModels,
  selectedExecutionModel,
  onExecutionModelChange,
  tradeDirectionOptions,
  selectedTradeDirection,
  onTradeDirectionChange,
  executionModelDefinition,
  executionParams,
  onExecutionParamChange,
  assumptions,
  onAssumptionChange,
  range,
  onRangeChange
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button className="bt-right-section-hdr" type="button" onClick={() => setOpen((v) => !v)}>
        <span>{open ? "▾" : "▸"} Advanced</span>
      </button>

      {open && (
        <>
          <div className="bt-right-field-row">
            <span className="bt-right-field-label">Execution</span>
            <select className="bt-right-select" value={selectedExecutionModel} onChange={(e) => onExecutionModelChange(e.target.value)}>
              {executionModels.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </div>

          <div className="bt-right-field-row">
            <span className="bt-right-field-label">Direction</span>
            <select className="bt-right-select" value={selectedTradeDirection} onChange={(e) => onTradeDirectionChange(e.target.value)}>
              {tradeDirectionOptions.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
            </select>
          </div>

          {executionModelDefinition?.parameters?.map((p) => (
            <div key={p.name} className="bt-right-field-row">
              <span className="bt-right-field-label">{p.label}</span>
              <input
                className="bt-right-input"
                type="number"
                value={executionParams[p.name] ?? ""}
                onChange={(e) => onExecutionParamChange(p.name, Number(e.target.value))}
              />
            </div>
          ))}
        </>
      )}
    </>
  );
}

export default function IndicatorParams({
  indicators,
  inputReferences,
  onUpdateIndicator,
  onRemoveIndicator,
  onAddIndicator,
  executionModels,
  selectedExecutionModel,
  onExecutionModelChange,
  tradeDirectionOptions,
  selectedTradeDirection,
  onTradeDirectionChange,
  executionModelDefinition,
  executionParams,
  onExecutionParamChange,
  assumptions,
  onAssumptionChange,
  range,
  onRangeChange
}) {
  return (
    <div className="bt-right">
      {/* Scrollable indicators — Advanced section stays pinned at bottom */}
      <div className="bt-right-scroll">
        {indicators.length === 0 && (
          <div style={{ padding: "16px 12px", color: "var(--bt-text3)", fontSize: 12, lineHeight: 1.6 }}>
            No active indicators.<br />
            <span style={{ fontSize: 11 }}>Add one from the left panel.</span>
          </div>
        )}

        {indicators.map((ind, i) => (
          <ParamBlock
            key={ind.id}
            indicator={ind}
            index={i}
            inputReferences={inputReferences}
            onUpdate={(id, next) => onUpdateIndicator(id, next)}
            onRemove={onRemoveIndicator}
          />
        ))}
      </div>

      {/* Advanced section — always visible at bottom, outside scroll */}
      <div className="bt-right-bottom">
        <AdvancedSection
          executionModels={executionModels}
          selectedExecutionModel={selectedExecutionModel}
          onExecutionModelChange={onExecutionModelChange}
          tradeDirectionOptions={tradeDirectionOptions}
          selectedTradeDirection={selectedTradeDirection}
          onTradeDirectionChange={onTradeDirectionChange}
          executionModelDefinition={executionModelDefinition}
          executionParams={executionParams}
          onExecutionParamChange={onExecutionParamChange}
          assumptions={assumptions}
          onAssumptionChange={onAssumptionChange}
          range={range}
          onRangeChange={onRangeChange}
        />

        <div className="bt-right-footer">
          <button
            className="bt-btn bt-btn--ghost"
            type="button"
            style={{ width: "100%", justifyContent: "center" }}
            onClick={() => document.querySelector(".bt-ind-search")?.focus()}
          >
            + Add indicator
          </button>
        </div>
      </div>
    </div>
  );
}
