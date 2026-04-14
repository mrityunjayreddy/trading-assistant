import { useSyncExternalStore } from "react";
import {
  buildDslPayload,
  cloneRuleNode,
  collectDslOptimizationTargets,
  countDslCombinations,
  createCondition,
  createIndicator,
  createOptimizationConfig,
  createRuleGroup,
  hydrateBuilderStateFromDsl,
  mapPresetToDsl,
  validateBuilderState
} from "../utils/strategyDsl";

const listeners = new Set();

let state = createInitialState();

function createInitialState() {
  const preset = {
    indicators: [],
    entryRules: createRuleGroup(),
    exitRules: createRuleGroup()
  };
  const optimizationConfig = Object.fromEntries(
    collectDslOptimizationTargets(preset.indicators).map((target) => [target.key, createOptimizationConfig(target)])
  );

  return {
    builderEnabled: false,
    templateStrategy: "CUSTOM",
    indicators: preset.indicators,
    entryRules: preset.entryRules,
    exitRules: preset.exitRules,
    optimizationConfig,
    validationMessages: validateBuilderState(preset)
  };
}

function emit() {
  listeners.forEach((listener) => listener());
}

function setState(updater) {
  const nextState = typeof updater === "function" ? updater(state) : updater;
  state = {
    ...nextState,
    validationMessages: validateBuilderState(nextState)
  };
  emit();
}

function rebuildOptimizationConfig(indicators, currentConfig) {
  const nextConfig = {};
  collectDslOptimizationTargets(indicators).forEach((target) => {
    nextConfig[target.key] = currentConfig[target.key] ?? createOptimizationConfig(target);
  });
  return nextConfig;
}

function updateRuleNode(node, ruleId, updater) {
  if (!node) {
    return node;
  }

  if (node.id === ruleId) {
    return updater(node);
  }

  if (node.kind === "group") {
    return {
      ...node,
      children: node.children.map((child) => updateRuleNode(child, ruleId, updater))
    };
  }

  return node;
}

function removeRuleNode(node, ruleId) {
  if (!node || node.kind !== "group") {
    return node;
  }

  const children = node.children
    .filter((child) => child.id !== ruleId)
    .map((child) => removeRuleNode(child, ruleId));

  return {
    ...node,
    children: children.length ? children : [createCondition()]
  };
}

export const strategyBuilderActions = {
  setBuilderEnabled(enabled) {
    setState((current) => ({
      ...current,
      builderEnabled: enabled
    }));
  },
  hydrateFromPreset(strategyValue, params) {
    const preset = mapPresetToDsl(strategyValue, params);
    setState((current) => ({
      ...current,
      templateStrategy: strategyValue,
      indicators: preset.indicators,
      entryRules: preset.entryRules,
      exitRules: preset.exitRules,
      optimizationConfig: rebuildOptimizationConfig(preset.indicators, current.optimizationConfig)
    }));
  },
  hydrateFromDsl(payload) {
    const nextBuilderState = hydrateBuilderStateFromDsl(payload);
    setState((current) => ({
      ...current,
      builderEnabled: true,
      templateStrategy: "CUSTOM",
      indicators: nextBuilderState.indicators,
      entryRules: nextBuilderState.entryRules,
      exitRules: nextBuilderState.exitRules,
      optimizationConfig: rebuildOptimizationConfig(nextBuilderState.indicators, current.optimizationConfig)
    }));
  },
  setTemplateStrategy(strategyValue) {
    setState((current) => ({
      ...current,
      templateStrategy: strategyValue
    }));
  },
  addIndicator(type) {
    setState((current) => {
      const indicators = [...current.indicators, createIndicator(type)];
      return {
        ...current,
        indicators,
        optimizationConfig: rebuildOptimizationConfig(indicators, current.optimizationConfig)
      };
    });
  },
  removeIndicator(indicatorId) {
    setState((current) => {
      const indicators = current.indicators.filter((indicator) => indicator.id !== indicatorId);
      return {
        ...current,
        indicators,
        optimizationConfig: rebuildOptimizationConfig(indicators, current.optimizationConfig)
      };
    });
  },
  updateIndicator(indicatorId, updater) {
    setState((current) => {
      const indicators = current.indicators.map((indicator) => (
        indicator.id === indicatorId ? updater(indicator) : indicator
      ));
      return {
        ...current,
        indicators,
        optimizationConfig: rebuildOptimizationConfig(indicators, current.optimizationConfig)
      };
    });
  },
  replaceRuleTree(scope, tree) {
    setState((current) => ({
      ...current,
      [scope]: cloneRuleNode(tree)
    }));
  },
  updateRule(scope, ruleId, updater) {
    setState((current) => ({
      ...current,
      [scope]: updateRuleNode(current[scope], ruleId, updater)
    }));
  },
  addRule(scope, parentId, kind) {
    setState((current) => ({
      ...current,
      [scope]: updateRuleNode(current[scope], parentId, (node) => ({
        ...node,
        children: [
          ...(node.children ?? []),
          kind === "group" ? createRuleGroup() : createCondition()
        ]
      }))
    }));
  },
  removeRule(scope, ruleId) {
    setState((current) => ({
      ...current,
      [scope]: current[scope]?.id === ruleId ? createRuleGroup() : removeRuleNode(current[scope], ruleId)
    }));
  },
  duplicateRule(scope, parentId, ruleId) {
    setState((current) => ({
      ...current,
      [scope]: updateRuleNode(current[scope], parentId, (node) => {
        const idx = node.children.findIndex((c) => c.id === ruleId);
        if (idx === -1) return node;
        const clone = cloneRuleNode(node.children[idx]);
        const next = [...node.children];
        next.splice(idx + 1, 0, clone);
        return { ...node, children: next };
      })
    }));
  },
  reorderRule(scope, parentId, ruleId, direction) {
    setState((current) => ({
      ...current,
      [scope]: updateRuleNode(current[scope], parentId, (node) => {
        const idx = node.children.findIndex((c) => c.id === ruleId);
        if (idx === -1) return node;
        const target = idx + direction;
        if (target < 0 || target >= node.children.length) return node;
        const next = [...node.children];
        [next[idx], next[target]] = [next[target], next[idx]];
        return { ...node, children: next };
      })
    }));
  },
  updateOptimizationConfig(key, field, value) {
    setState((current) => ({
      ...current,
      optimizationConfig: {
        ...current.optimizationConfig,
        [key]: {
          ...current.optimizationConfig[key],
          [field]: value
        }
      }
    }));
  }
};

export function getStrategyBuilderState() {
  return state;
}

export function useStrategyBuilderStore(selector = (snapshot) => snapshot) {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => selector(state)
  );
}

export function selectBuilderPayload() {
  return buildDslPayload(state);
}

export function selectBuilderCombinationCount() {
  return countDslCombinations(state.optimizationConfig);
}
