import Panel from "../../shared/Panel";

export default function JsonStrategyEditor({
  error,
  isDirty,
  onApply,
  onClose,
  onReset,
  onToggleSync,
  syncEnabled,
  validationMessages,
  value,
  onChange
}) {
  return (
    <Panel className="tv-json-editor">
      <div className="tv-panel__header">
        <div>
          <span className="tv-panel__eyebrow">JSON Editor</span>
          <h3>Strategy DSL</h3>
          <p>Edit the same strategy in code form. Apply valid JSON to push it back into the visual builder.</p>
        </div>
        <div className="tv-json-editor__actions">
          <button className={`tv-chip-button ${syncEnabled ? "tv-chip-button--active" : ""}`} type="button" onClick={onToggleSync}>
            {syncEnabled ? "Auto Sync On" : "Auto Sync Off"}
          </button>
          <button className="tv-chip-button" type="button" onClick={onReset} disabled={!isDirty}>
            Reset
          </button>
          <button className="tv-chip-button tv-chip-button--primary" type="button" onClick={onApply}>
            Apply JSON
          </button>
          <button className="tv-chip-button" type="button" onClick={onClose}>
            Close
          </button>
        </div>
      </div>

      <textarea
        className="tv-json-editor__textarea"
        spellCheck="false"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />

      {error ? <div className="tv-banner tv-banner--error">{error}</div> : null}
      {!error && validationMessages.length ? (
        <div className="tv-json-editor__validation">
          {validationMessages.map((message) => (
            <div className="tv-banner" key={message}>{message}</div>
          ))}
        </div>
      ) : null}
    </Panel>
  );
}
