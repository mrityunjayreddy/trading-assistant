import Panel from "../../shared/Panel";
import FieldTooltip from "./FieldTooltip";
import { INDICATOR_LIBRARY } from "../../../utils/strategyDsl";

export default function IndicatorBuilderPanel({
  indicatorOptions,
  indicators,
  onAddIndicator,
  onRemoveIndicator,
  onUpdateIndicator
}) {
  return (
    <Panel className="builder-section">
      <div className="builder-section__header">
        <div>
          <span className="builder-section__eyebrow">1. Indicator Builder</span>
          <h3>Compose your signal inputs</h3>
          <p>Add indicators, rename them, and tune their parameters before wiring rules.</p>
        </div>
        <div className="builder-toolbar">
          <select
            className="builder-input"
            defaultValue="MA"
            onChange={(event) => {
              onAddIndicator(event.target.value);
              event.target.value = "MA";
            }}
          >
            {indicatorOptions.map((option) => (
              <option key={option.value} value={option.value}>
                Add {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="builder-indicator-grid">
        {indicators.map((indicator) => {
          const definition = INDICATOR_LIBRARY[indicator.type];

          return (
            <article className="builder-card" key={indicator.id}>
              <div className="builder-card__header">
                <div>
                  <span className="builder-pill">{definition.label}</span>
                  <h4>{indicator.id}</h4>
                  <p>{definition.description}</p>
                </div>
                <button
                  className="builder-ghost-button"
                  type="button"
                  onClick={() => onRemoveIndicator(indicator.id)}
                >
                  Remove
                </button>
              </div>

              <div className="builder-form-grid">
                <label className="builder-field">
                  <span className="builder-field__label">
                    Indicator Id
                    <FieldTooltip text="Use short, unique ids. Rule references and optimization paths depend on this id." />
                  </span>
                  <input
                    className="builder-input"
                    value={indicator.id}
                    onChange={(event) => onUpdateIndicator(indicator.id, {
                      ...indicator,
                      id: event.target.value.replace(/\s+/g, "")
                    })}
                  />
                </label>

                {definition.params.map((field) => (
                  <label className="builder-field" key={`${indicator.id}-${field.name}`}>
                    <span className="builder-field__label">
                      {field.label}
                      <FieldTooltip text={field.tooltip} />
                    </span>
                    {field.type === "select" ? (
                      <select
                        className="builder-input"
                        value={indicator.params[field.name]}
                        onChange={(event) => onUpdateIndicator(indicator.id, {
                          ...indicator,
                          params: {
                            ...indicator.params,
                            [field.name]: event.target.value
                          }
                        })}
                      >
                        {field.options.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input
                        className="builder-input"
                        type="number"
                        min={field.min}
                        max={field.max}
                        step={field.step}
                        value={indicator.params[field.name]}
                        onChange={(event) => onUpdateIndicator(indicator.id, {
                          ...indicator,
                          params: {
                            ...indicator.params,
                            [field.name]: Number(event.target.value)
                          }
                        })}
                      />
                    )}
                  </label>
                ))}
              </div>
            </article>
          );
        })}
      </div>
    </Panel>
  );
}
