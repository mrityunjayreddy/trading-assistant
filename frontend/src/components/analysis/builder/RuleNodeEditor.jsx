import FieldTooltip from "./FieldTooltip";
import {
  buildMissingIndicatorReferenceOptions,
  DUAL_VALUE_OPERATORS,
  RULE_GROUP_OPTIONS,
  RULE_OPERATOR_OPTIONS,
  VALUE_ONLY_OPERATORS
} from "../../../utils/strategyDsl";

const OPERATOR_HINT = {
  IS_BETWEEN: "Two thresholds — value must fall inside the range.",
  INCREASED_BY_PCT: "Percentage rise from N bars ago.",
  NBAR_HIGH: "True when value equals the highest point over N bars.",
  NBAR_LOW: "True when value equals the lowest point over N bars.",
  CROSS_ABOVE: "True on the bar where the left side moves above the right.",
  CROSS_BELOW: "True on the bar where the left side moves below the right."
};

function getRightSideType(operator) {
  if (DUAL_VALUE_OPERATORS.has(operator)) return "dual";
  if (VALUE_ONLY_OPERATORS.has(operator)) return "single";
  return "comparison";
}

function getRightLabel(operator) {
  if (operator === "IS_BETWEEN") return "Low end";
  if (operator === "INCREASED_BY_PCT") return "Rise %";
  if (operator === "NBAR_HIGH" || operator === "NBAR_LOW") return "N bars";
  return "Threshold";
}

function getRightValue2Label(operator) {
  if (operator === "IS_BETWEEN") return "High end";
  if (operator === "INCREASED_BY_PCT") return "Over N bars";
  return "Value 2";
}

// ── Condition leaf ─────────────────────────────────────────────────────────

function ConditionRow({
  node,
  index,
  totalSiblings,
  parentId,
  indicatorReferences,
  onUpdateRule,
  onRemoveRule,
  onDuplicateRule,
  onMoveRule
}) {
  const availableRefs = buildMissingIndicatorReferenceOptions(indicatorReferences, [node.left, node.rightIndicator]);
  const rightSideType = getRightSideType(node.operator);
  const hint = OPERATOR_HINT[node.operator];

  function update(patch) {
    onUpdateRule(node.id, { ...node, ...patch });
  }

  return (
    <div className="builder-rule-condition">
      <div className="builder-condition-index">#{index + 1}</div>

      <div className="builder-form-grid builder-form-grid--rule">
        {/* Left side */}
        <label className="builder-field">
          <span className="builder-field__label">
            Left Side
            <FieldTooltip text="The indicator or price series being evaluated." />
          </span>
          <select
            className="builder-input"
            value={node.left}
            onChange={(e) => update({ left: e.target.value })}
          >
            {availableRefs.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </label>

        {/* Operator */}
        <label className="builder-field">
          <span className="builder-field__label">
            Operator
            <FieldTooltip text={hint ?? "How to compare left and right sides."} />
          </span>
          <select
            className="builder-input"
            value={node.operator}
            onChange={(e) => update({ operator: e.target.value })}
          >
            {RULE_OPERATOR_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </label>

        {/* Right side — mode selector (only for comparison operators) */}
        {rightSideType === "comparison" && (
          <label className="builder-field">
            <span className="builder-field__label">
              Compare Against
              <FieldTooltip text="Switch between a numeric threshold and another indicator." />
            </span>
            <select
              className="builder-input"
              value={node.rightMode}
              onChange={(e) => update({ rightMode: e.target.value })}
            >
              <option value="value">Numeric Value</option>
              <option value="indicator">Indicator</option>
            </select>
          </label>
        )}

        {/* Right side — actual input(s) */}
        {rightSideType === "comparison" && node.rightMode === "indicator" ? (
          <label className="builder-field">
            <span className="builder-field__label">
              Right Indicator
              <FieldTooltip text="Another indicator or price stream to compare against." />
            </span>
            <select
              className="builder-input"
              value={node.rightIndicator}
              onChange={(e) => update({ rightIndicator: e.target.value })}
            >
              <option value="">Select an indicator</option>
              {availableRefs.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>
        ) : (
          <label className="builder-field">
            <span className="builder-field__label">
              {getRightLabel(node.operator)}
              <FieldTooltip text="Numeric threshold for this condition." />
            </span>
            <input
              className="builder-input"
              type="number"
              step="any"
              value={node.rightValue}
              onChange={(e) => update({ rightValue: Number(e.target.value) })}
            />
          </label>
        )}

        {/* Second threshold for dual-value operators */}
        {DUAL_VALUE_OPERATORS.has(node.operator) && (
          <label className="builder-field">
            <span className="builder-field__label">
              {getRightValue2Label(node.operator)}
              <FieldTooltip text="Second numeric value for this operator." />
            </span>
            <input
              className="builder-input"
              type="number"
              step="any"
              value={node.rightValue2 ?? 0}
              onChange={(e) => update({ rightValue2: Number(e.target.value) })}
            />
          </label>
        )}
      </div>

      {/* Row actions */}
      <div className="builder-rule-condition__footer">
        <div className="builder-condition-actions">
          <button
            className="builder-condition-action"
            type="button"
            title="Move up"
            disabled={index === 0}
            onClick={() => onMoveRule(parentId, node.id, -1)}
          >
            ↑
          </button>
          <button
            className="builder-condition-action"
            type="button"
            title="Move down"
            disabled={index === totalSiblings - 1}
            onClick={() => onMoveRule(parentId, node.id, 1)}
          >
            ↓
          </button>
          <button
            className="builder-condition-action"
            type="button"
            title="Duplicate condition"
            onClick={() => onDuplicateRule(parentId, node.id)}
          >
            ⧉
          </button>
        </div>
        <button className="builder-ghost-button" type="button" onClick={() => onRemoveRule(node.id)}>
          Remove
        </button>
      </div>
    </div>
  );
}

// ── Recursive node renderer ────────────────────────────────────────────────

export default function RuleNodeEditor({
  node,
  parentId,
  childIndex,
  totalSiblings,
  indicatorReferences,
  onAddRule,
  onRemoveRule,
  onUpdateRule,
  onDuplicateRule,
  onMoveRule
}) {
  if (node.kind === "group") {
    const children = node.children ?? [];
    return (
      <div className="builder-rule-group">
        <div className="builder-rule-group__toolbar">
          <label className="builder-field">
            <span className="builder-field__label">
              Logic
              <FieldTooltip text="AND requires all conditions. OR requires any one condition." />
            </span>
            <select
              className="builder-input"
              value={node.logicalOperator}
              onChange={(e) => onUpdateRule(node.id, { ...node, logicalOperator: e.target.value })}
            >
              {RULE_GROUP_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>

          <div className="builder-toolbar">
            <button className="builder-secondary-button" type="button" onClick={() => onAddRule(node.id, "condition")}>
              + Condition
            </button>
            <button className="builder-secondary-button" type="button" onClick={() => onAddRule(node.id, "group")}>
              + Group
            </button>
            {parentId && (
              <button className="builder-ghost-button" type="button" onClick={() => onRemoveRule(node.id)}>
                Delete Group
              </button>
            )}
          </div>
        </div>

        <div className="builder-rule-children">
          {children.map((child, index) => (
            <div key={child.id}>
              {index > 0 && (
                <div className="builder-logic-junction">
                  <span className={`builder-logic-badge builder-logic-badge--${node.logicalOperator.toLowerCase()}`}>
                    {node.logicalOperator}
                  </span>
                </div>
              )}
              <RuleNodeEditor
                node={child}
                parentId={node.id}
                childIndex={index}
                totalSiblings={children.length}
                indicatorReferences={indicatorReferences}
                onAddRule={onAddRule}
                onRemoveRule={onRemoveRule}
                onUpdateRule={onUpdateRule}
                onDuplicateRule={onDuplicateRule}
                onMoveRule={onMoveRule}
              />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <ConditionRow
      node={node}
      index={childIndex ?? 0}
      totalSiblings={totalSiblings ?? 1}
      parentId={parentId ?? "root"}
      indicatorReferences={indicatorReferences}
      onUpdateRule={onUpdateRule}
      onRemoveRule={onRemoveRule}
      onDuplicateRule={onDuplicateRule}
      onMoveRule={onMoveRule}
    />
  );
}
