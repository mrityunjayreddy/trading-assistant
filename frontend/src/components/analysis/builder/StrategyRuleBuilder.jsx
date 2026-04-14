import { useState } from "react";
import Panel from "../../shared/Panel";
import RuleNodeEditor from "./RuleNodeEditor";
import {
  buildConditionTemplate,
  buildRuleSummary,
  CONDITION_TEMPLATE_OPTIONS
} from "../../../utils/strategyDsl";

export default function StrategyRuleBuilder({
  title,
  subtitle,
  indicatorReferences,
  ruleTree,
  onAddRule,
  onRemoveRule,
  onUpdateRule,
  onDuplicateRule,
  onMoveRule,
  onApplyTemplate
}) {
  const [templateKey, setTemplateKey] = useState("");

  const summary = buildRuleSummary(ruleTree, indicatorReferences);

  function handleApplyTemplate() {
    if (!templateKey || !onApplyTemplate) return;
    const payload = buildConditionTemplate(templateKey);
    if (payload) onApplyTemplate(payload);
    setTemplateKey("");
  }

  return (
    <Panel className="builder-section">
      <div className="builder-section__header">
        <div>
          <span className="builder-section__eyebrow">{title}</span>
          <h3>{subtitle}</h3>
          <p>Build nested rule trees with AND / OR logic and cross conditions.</p>
        </div>
      </div>

      {/* Template strip */}
      {onApplyTemplate && (
        <div className="builder-template-strip">
          <select
            className="builder-input builder-template-strip__select"
            value={templateKey}
            onChange={(e) => setTemplateKey(e.target.value)}
          >
            <option value="">Load a template…</option>
            {CONDITION_TEMPLATE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <button
            className="builder-secondary-button"
            type="button"
            disabled={!templateKey}
            onClick={handleApplyTemplate}
          >
            Apply
          </button>
        </div>
      )}

      {/* Plain-English summary */}
      {summary && (
        <div className="builder-rule-summary">
          <span className="builder-rule-summary__label">Summary</span>
          <p className="builder-rule-summary__text">{summary}</p>
        </div>
      )}

      <RuleNodeEditor
        indicatorReferences={indicatorReferences}
        node={ruleTree}
        onAddRule={onAddRule}
        onRemoveRule={onRemoveRule}
        onUpdateRule={onUpdateRule}
        onDuplicateRule={onDuplicateRule}
        onMoveRule={onMoveRule}
      />
    </Panel>
  );
}
