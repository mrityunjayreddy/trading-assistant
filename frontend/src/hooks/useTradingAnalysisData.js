import { useEffect, useMemo, useRef, useState } from "react";
import { TradingSimulationService } from "../services/analysis/TradingSimulationService";
import {
  buildDslParamGrid,
  buildDslPayload,
  buildIndicatorReferenceOptions,
  collectDslOptimizationTargets,
  countDslCombinations,
  createOptimizationConfig,
  getIndicatorDisplayOptions,
  STRATEGY_BUILDER_TEMPLATES
} from "../utils/strategyDsl";
import { strategyBuilderActions, useStrategyBuilderStore } from "../state/strategyBuilderStore";

const FALLBACK_SYMBOLS = ["BTCUSDT", "ETHUSDT", "BNBUSDT"];
const FALLBACK_STRATEGIES = [
  {
    value: "BUY_AND_HOLD",
    label: "Buy And Hold",
    description: "Buys once at the first available candle and holds until the simulation ends.",
    parameters: []
  },
  {
    value: "MA_CROSSOVER",
    label: "Moving Average Crossover",
    description: "Buys on bullish MA crossover and sells on bearish crossover.",
    parameters: [
      { name: "shortWindow", label: "Short Window", type: "number", defaultValue: 10, minValue: 1, maxValue: 499, required: true },
      { name: "longWindow", label: "Long Window", type: "number", defaultValue: 50, minValue: 2, maxValue: 500, required: true }
    ]
  },
  {
    value: "RSI",
    label: "RSI Reversal",
    description: "Enters when RSI exits oversold and exits when it leaves overbought territory.",
    parameters: [
      { name: "period", label: "Period", type: "number", defaultValue: 14, minValue: 2, maxValue: 200, required: true },
      { name: "oversold", label: "Oversold", type: "number", defaultValue: 30, minValue: 1, maxValue: 50, required: true },
      { name: "overbought", label: "Overbought", type: "number", defaultValue: 70, minValue: 50, maxValue: 99, required: true }
    ]
  },
  {
    value: "BOLLINGER_BANDS",
    label: "Bollinger Bands",
    description: "Enters on lower-band recovery and exits on upper-band rejection.",
    parameters: [
      { name: "window", label: "Window", type: "number", defaultValue: 20, minValue: 2, maxValue: 300, required: true },
      { name: "stdDevMultiplier", label: "Std Dev", type: "number", defaultValue: 2, minValue: 0.5, maxValue: 5, required: true }
    ]
  }
];
const FALLBACK_EXECUTION_MODELS = [
  {
    value: "FULL_BALANCE",
    label: "Full Balance",
    description: "Deploys the full available balance on each BUY signal.",
    parameters: []
  },
  {
    value: "PERCENT_OF_BALANCE",
    label: "Percent Of Balance",
    description: "Allocates a configurable percentage of cash balance on each BUY signal.",
    parameters: [
      { name: "allocationPercent", label: "Allocation Percent", type: "number", defaultValue: 25, minValue: 1, maxValue: 100, required: true }
    ]
  },
  {
    value: "FIXED_AMOUNT",
    label: "Fixed Amount",
    description: "Allocates a fixed USDT amount per BUY signal.",
    parameters: [
      { name: "fixedAmount", label: "Fixed Amount (USDT)", type: "number", defaultValue: 250, minValue: 1, maxValue: 1000000, required: true }
    ]
  }
];
const INTERVAL_OPTIONS = [
  { value: "1m", label: "1 Minute" },
  { value: "5m", label: "5 Minutes" },
  { value: "15m", label: "15 Minutes" },
  { value: "1h", label: "1 Hour" },
  { value: "4h", label: "4 Hour" },
  { value: "1d", label: "1 Day" },
  { value: "1w", label: "1 Week" }
];
const ANALYSIS_MODES = [
  { value: "SIMULATION", label: "Simulation" },
  { value: "OPTIMIZATION", label: "Optimization" }
];
const TRADE_DIRECTION_OPTIONS = [
  { value: "LONG_ONLY", label: "Long Only" },
  { value: "SHORT_ONLY", label: "Short Only" },
  { value: "BOTH", label: "Long + Short" }
];
const METRIC_OPTIONS = [
  { value: "TOTAL_RETURN", label: "Total Return" },
  { value: "SHARPE_RATIO", label: "Sharpe Ratio" },
  { value: "MAX_DRAWDOWN", label: "Max Drawdown" },
  { value: "WIN_RATE", label: "Win Rate" }
];

const tradingSimulationService = new TradingSimulationService();

function toDateTimeLocalValue(date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function createDefaultRange() {
  const end = new Date();
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000);
  return {
    start: toDateTimeLocalValue(start),
    end: toDateTimeLocalValue(end)
  };
}

function inferStep(parameter) {
  const values = [parameter.defaultValue, parameter.minValue, parameter.maxValue];
  return values.some((value) => Number.isFinite(value) && !Number.isInteger(Number(value))) ? 0.5 : 1;
}

function createOptimizationRange(parameter) {
  const defaultValue = Number(parameter.defaultValue ?? parameter.minValue ?? 1);
  const step = inferStep(parameter);
  const fallbackEnd = Number(parameter.maxValue ?? (defaultValue + step * 2));
  const end = Number.isFinite(fallbackEnd) ? Math.min(fallbackEnd, defaultValue + step * 2) : defaultValue + step * 2;
  return {
    start: defaultValue,
    end: Math.max(defaultValue, end),
    step
  };
}

function roundValue(value, step) {
  const normalizedStep = Number(step);
  if (!Number.isFinite(normalizedStep) || normalizedStep <= 0) {
    return value;
  }
  const decimals = `${normalizedStep}`.includes(".") ? `${normalizedStep}`.split(".")[1].length : 0;
  return Number(value.toFixed(Math.min(decimals + 2, 6)));
}

function buildGridValues(config) {
  const start = Number(config.start);
  const end = Number(config.end);
  const step = Number(config.step);

  if (!Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(step) || step <= 0 || start > end) {
    return [];
  }

  const values = [];
  const stepsCount = Math.floor((end - start) / step);
  for (let index = 0; index <= stepsCount; index += 1) {
    values.push(roundValue(start + step * index, step));
  }
  if (values[values.length - 1] !== roundValue(end, step)) {
    values.push(roundValue(end, step));
  }
  return values;
}

export function useTradingAnalysisData(symbols) {
  const hasAutoRunRef = useRef(false);
  const builderState = useStrategyBuilderStore((snapshot) => snapshot);
  const [analysisMode, setAnalysisMode] = useState("SIMULATION");
  const [selectedSymbol, setSelectedSymbol] = useState("BTCUSDT");
  const [selectedInterval, setSelectedInterval] = useState("5m");
  const [strategies, setStrategies] = useState(FALLBACK_STRATEGIES);
  const [selectedStrategy, setSelectedStrategy] = useState("MA_CROSSOVER");
  const [strategyParams, setStrategyParams] = useState({
    shortWindow: 10,
    longWindow: 50,
    period: 14,
    oversold: 30,
    overbought: 70,
    window: 20,
    stdDevMultiplier: 2
  });
  const [optimizationParams, setOptimizationParams] = useState({});
  const [optimizationMetric, setOptimizationMetric] = useState("TOTAL_RETURN");
  const [optimizationResult, setOptimizationResult] = useState(null);
  const [executionModels, setExecutionModels] = useState(FALLBACK_EXECUTION_MODELS);
  const [selectedExecutionModel, setSelectedExecutionModel] = useState("FULL_BALANCE");
  const [selectedTradeDirection, setSelectedTradeDirection] = useState("LONG_ONLY");
  const [executionParams, setExecutionParams] = useState({
    allocationPercent: 25,
    fixedAmount: 250
  });
  const [assumptions, setAssumptions] = useState({
    initialBalance: 1000,
    feeRate: 0.0004
  });
  const [range, setRange] = useState(createDefaultRange);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [simulationResult, setSimulationResult] = useState(null);
  const [lastRunAt, setLastRunAt] = useState(null);
  const availableSymbols = symbols.length ? symbols : FALLBACK_SYMBOLS;

  const selectedStrategyDefinition = useMemo(
    () => strategies.find((strategy) => strategy.value === selectedStrategy) ?? strategies[0] ?? FALLBACK_STRATEGIES[0],
    [selectedStrategy, strategies]
  );
  const selectedExecutionModelDefinition = useMemo(
    () => executionModels.find((model) => model.value === selectedExecutionModel) ?? executionModels[0] ?? FALLBACK_EXECUTION_MODELS[0],
    [executionModels, selectedExecutionModel]
  );

  const indicatorOptions = useMemo(
    () => getIndicatorDisplayOptions(),
    []
  );

  const dslPayload = useMemo(() => buildDslPayload(builderState), [builderState]);
  const dslOptimizationTargets = useMemo(() => collectDslOptimizationTargets(builderState.indicators), [builderState.indicators]);
  const dslCombinationCount = useMemo(() => countDslCombinations(builderState.optimizationConfig), [builderState.optimizationConfig]);
  const indicatorReferenceOptions = useMemo(
    () => buildIndicatorReferenceOptions(builderState.indicators),
    [builderState.indicators]
  );

  useEffect(() => {
    setSelectedSymbol((currentSymbol) => (
      availableSymbols.includes(currentSymbol) ? currentSymbol : availableSymbols[0]
    ));
  }, [availableSymbols]);

  useEffect(() => {
    let active = true;
    tradingSimulationService.fetchStrategies()
      .then((payload) => {
        if (!active || !payload.length) {
          return;
        }
        setStrategies(payload);
        setSelectedStrategy((current) => (
          payload.some((strategy) => strategy.value === current) ? current : payload[0].value
        ));
      })
      .catch(() => {
        if (active) {
          setStrategies(FALLBACK_STRATEGIES);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    tradingSimulationService.fetchExecutionModels()
      .then((payload) => {
        if (!active || !payload.length) {
          return;
        }
        setExecutionModels(payload);
        setSelectedExecutionModel((current) => (
          payload.some((model) => model.value === current) ? current : payload[0].value
        ));
      })
      .catch(() => {
        if (active) {
          setExecutionModels(FALLBACK_EXECUTION_MODELS);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedStrategyDefinition) {
      return;
    }

    setStrategyParams((current) => {
      const next = { ...current };
      selectedStrategyDefinition.parameters.forEach((parameter) => {
        if (next[parameter.name] == null) {
          next[parameter.name] = Number(parameter.defaultValue ?? 0);
        }
      });
      return next;
    });

    setOptimizationParams((current) => {
      const next = { ...current };
      selectedStrategyDefinition.parameters.forEach((parameter) => {
        if (!next[parameter.name]) {
          next[parameter.name] = createOptimizationRange(parameter);
        }
      });
      return next;
    });
  }, [selectedStrategyDefinition]);

  useEffect(() => {
    if (!selectedExecutionModelDefinition) {
      return;
    }

    setExecutionParams((current) => {
      const next = { ...current };
      selectedExecutionModelDefinition.parameters.forEach((parameter) => {
        if (next[parameter.name] == null) {
          next[parameter.name] = parameter.defaultValue ?? 0;
        }
      });
      return next;
    });
  }, [selectedExecutionModelDefinition]);

  const legacyOptimizationCombinationCount = useMemo(() => {
    if (!selectedStrategyDefinition?.parameters.length) {
      return 0;
    }
    return selectedStrategyDefinition.parameters.reduce((total, parameter) => {
      const values = buildGridValues(optimizationParams[parameter.name] ?? {});
      return total * Math.max(values.length, 0);
    }, 1);
  }, [optimizationParams, selectedStrategyDefinition]);

  const optimizationCombinationCount = builderState.builderEnabled ? dslCombinationCount : legacyOptimizationCombinationCount;

  const validationMessage = useMemo(() => {
    const builderMessages = builderState.builderEnabled ? builderState.validationMessages : [];
    if (builderMessages.length) {
      return builderMessages[0];
    }

    if (!selectedStrategyDefinition) {
      return "";
    }

    if (!builderState.builderEnabled && analysisMode === "SIMULATION") {
      for (const parameter of selectedStrategyDefinition.parameters) {
        const value = Number(strategyParams[parameter.name]);
        if (parameter.required && (!Number.isFinite(value) || value <= 0)) {
          return `${parameter.label} must be a positive number.`;
        }
        if (parameter.minValue != null && value < parameter.minValue) {
          return `${parameter.label} must be at least ${parameter.minValue}.`;
        }
        if (parameter.maxValue != null && value > parameter.maxValue) {
          return `${parameter.label} must be no more than ${parameter.maxValue}.`;
        }
      }
      if (selectedStrategy === "MA_CROSSOVER" && Number(strategyParams.shortWindow) >= Number(strategyParams.longWindow)) {
        return "Short window must be smaller than long window.";
      }
    }

    if (analysisMode === "OPTIMIZATION") {
      if (builderState.builderEnabled) {
        if (!dslOptimizationTargets.length) {
          return "Add at least one numeric DSL indicator parameter before running optimization.";
        }
        if (optimizationCombinationCount <= 0) {
          return "Optimization ranges must create at least one combination.";
        }
      } else {
        if (!selectedStrategyDefinition.parameters.length) {
          return "Selected strategy has no tunable parameters for optimization.";
        }
        for (const parameter of selectedStrategyDefinition.parameters) {
          const config = optimizationParams[parameter.name];
          const start = Number(config?.start);
          const end = Number(config?.end);
          const step = Number(config?.step);
          if (!Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(step)) {
            return `${parameter.label} optimization range must be numeric.`;
          }
          if (parameter.minValue != null && start < parameter.minValue) {
            return `${parameter.label} range start must be at least ${parameter.minValue}.`;
          }
          if (parameter.maxValue != null && end > parameter.maxValue) {
            return `${parameter.label} range end must be no more than ${parameter.maxValue}.`;
          }
          if (step <= 0) {
            return `${parameter.label} optimization step must be greater than zero.`;
          }
          if (start > end) {
            return `${parameter.label} range start must be before its end.`;
          }
        }
      }
    }

    if (selectedExecutionModelDefinition) {
      for (const parameter of selectedExecutionModelDefinition.parameters) {
        const value = Number(executionParams[parameter.name]);
        if (parameter.required && (!Number.isFinite(value) || value <= 0)) {
          return `${parameter.label} must be a positive number.`;
        }
      }
    }

    if (!Number.isFinite(Number(assumptions.initialBalance)) || Number(assumptions.initialBalance) <= 0) {
      return "Initial balance must be greater than zero.";
    }
    if (!Number.isFinite(Number(assumptions.feeRate)) || Number(assumptions.feeRate) < 0 || Number(assumptions.feeRate) >= 1) {
      return "Fee rate must be between 0 and 1.";
    }

    const startMs = Date.parse(range.start);
    const endMs = Date.parse(range.end);
    if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || startMs >= endMs) {
      return "Start date must be before end date.";
    }

    return "";
  }, [
    analysisMode,
    assumptions,
    builderState,
    dslOptimizationTargets.length,
    executionParams,
    optimizationCombinationCount,
    optimizationParams,
    range.end,
    range.start,
    selectedExecutionModelDefinition,
    selectedStrategy,
    selectedStrategyDefinition,
    strategyParams
  ]);

  function updateStrategyParam(name, value) {
    setStrategyParams((current) => ({
      ...current,
      [name]: value
    }));
  }

  function updateOptimizationParam(name, field, value) {
    setOptimizationParams((current) => ({
      ...current,
      [name]: {
        ...current[name],
        [field]: value
      }
    }));
  }

  function updateExecutionParam(name, value) {
    setExecutionParams((current) => ({
      ...current,
      [name]: value
    }));
  }

  function updateAssumption(name, value) {
    setAssumptions((current) => ({
      ...current,
      [name]: value
    }));
  }

  function updateRange(name, value) {
    setRange((current) => ({
      ...current,
      [name]: value
    }));
  }

  function loadPresetIntoBuilder() {
    strategyBuilderActions.hydrateFromPreset(selectedStrategy, strategyParams);
  }

  async function runSimulation() {
    if (!selectedSymbol) {
      return;
    }
    setLoading(true);
    setErrorMessage("");

    try {
      const payload = await tradingSimulationService.simulate({
        symbol: selectedSymbol,
        interval: selectedInterval,
        // When the DSL builder is active, send "DSL" as the strategy name placeholder
        // (backend ignores the name when indicators+rules are present) and include
        // the custom indicators/rules. Preset strategy/params are omitted.
        ...(builderState.builderEnabled
          ? { strategy: "DSL", params: {}, ...dslPayload }
          : { strategy: selectedStrategy, params: strategyParams }),
        execution: {
          type: selectedExecutionModel,
          params: executionParams,
          tradeDirection: selectedTradeDirection
        },
        assumptions,
        range: {
          startTime: Date.parse(range.start),
          endTime: Date.parse(range.end)
        }
      });
      setSimulationResult(payload);
      setOptimizationResult(null);
      setLastRunAt(Date.now());
    } catch (error) {
      setErrorMessage(error.message);
      setSimulationResult(null);
    } finally {
      setLoading(false);
    }
  }

  async function runOptimization() {
    if (!selectedSymbol) {
      return;
    }
    setLoading(true);
    setErrorMessage("");

    try {
      const paramGrid = builderState.builderEnabled
        ? buildDslParamGrid(builderState.optimizationConfig)
        : Object.fromEntries(
          (selectedStrategyDefinition?.parameters ?? []).map((parameter) => [
            parameter.name,
            buildGridValues(optimizationParams[parameter.name] ?? {})
          ])
        );

      const payload = await tradingSimulationService.optimize({
        symbol: selectedSymbol,
        interval: selectedInterval,
        // When the DSL builder is active, send "DSL" as the strategy name placeholder
        // and include the custom indicators/rules. Preset strategy is omitted.
        ...(builderState.builderEnabled
          ? { strategy: "DSL", params: {}, ...dslPayload }
          : { strategy: selectedStrategy }),
        paramGrid,
        metric: optimizationMetric,
        execution: {
          type: selectedExecutionModel,
          params: executionParams,
          tradeDirection: selectedTradeDirection
        },
        assumptions,
        range: {
          startTime: Date.parse(range.start),
          endTime: Date.parse(range.end)
        }
      });

      setOptimizationResult(payload);
      setSimulationResult(null);
      setLastRunAt(Date.now());
    } catch (error) {
      setErrorMessage(error.message);
      setOptimizationResult(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (hasAutoRunRef.current || !selectedSymbol || validationMessage) {
      return;
    }
    hasAutoRunRef.current = true;
    runSimulation();
  }, [selectedSymbol, validationMessage]);

  const summary = useMemo(() => {
    if (analysisMode === "OPTIMIZATION") {
      if (!optimizationResult) {
        return {
          bestScore: 0,
          evaluatedCombinations: optimizationCombinationCount,
          successfulCombinations: 0,
          metricUsed: optimizationMetric
        };
      }

      return {
        bestScore: optimizationResult.bestScore,
        evaluatedCombinations: optimizationResult.evaluatedCombinations,
        successfulCombinations: optimizationResult.successfulCombinations,
        metricUsed: optimizationResult.metricUsed
      };
    }

    if (!simulationResult) {
      return {
        finalBalance: 0,
        initialBalance: 0,
        totalReturn: 0,
        tradesCount: 0
      };
    }

    const finalBalance = simulationResult.finalBalance ?? 0;
    const initialBalance = simulationResult.initialBalance ?? 0;
    const totalReturn = initialBalance > 0
      ? (finalBalance - initialBalance) / initialBalance
      : 0;

    return {
      finalBalance,
      initialBalance,
      totalReturn,
      tradesCount: simulationResult.tradesCount
    };
  }, [
    analysisMode,
    optimizationCombinationCount,
    optimizationMetric,
    optimizationResult,
    simulationResult
  ]);

  const requestPreview = useMemo(() => JSON.stringify({
    symbol: selectedSymbol,
    interval: selectedInterval,
    strategy: selectedStrategy,
    execution: {
      type: selectedExecutionModel,
      params: executionParams,
      tradeDirection: selectedTradeDirection
    },
    assumptions,
    range: {
      startTime: Date.parse(range.start),
      endTime: Date.parse(range.end)
    },
    ...(analysisMode === "OPTIMIZATION"
      ? {
        metric: optimizationMetric,
        paramGrid: builderState.builderEnabled
          ? buildDslParamGrid(builderState.optimizationConfig)
          : Object.fromEntries(
            (selectedStrategyDefinition?.parameters ?? []).map((parameter) => [
              parameter.name,
              buildGridValues(optimizationParams[parameter.name] ?? {})
            ])
          )
      }
      : {}),
    ...(builderState.builderEnabled ? dslPayload : { params: strategyParams })
  }, null, 2), [
    analysisMode,
    assumptions,
    builderState.builderEnabled,
    builderState.optimizationConfig,
    dslPayload,
    executionParams,
    optimizationMetric,
    optimizationParams,
    range.end,
    range.start,
    selectedExecutionModel,
    selectedInterval,
    selectedStrategy,
    selectedStrategyDefinition,
    selectedSymbol,
    selectedTradeDirection,
    strategyParams
  ]);

  const dslPreview = useMemo(() => JSON.stringify(dslPayload, null, 2), [dslPayload]);

  return {
    analysisMode,
    analysisModes: ANALYSIS_MODES,
    availableSymbols,
    assumptions,
    builderState,
    dslPreview,
    errorMessage,
    executionModelDefinition: selectedExecutionModelDefinition,
    executionModels,
    executionParams,
    indicatorOptions,
    indicatorReferenceOptions,
    intervalOptions: INTERVAL_OPTIONS,
    lastRunAt,
    loading,
    metricOptions: METRIC_OPTIONS,
    optimizationCombinationCount,
    optimizationMetric,
    optimizationParams,
    optimizationResult,
    optimizationTargets: dslOptimizationTargets,
    range,
    requestPreview,
    selectedExecutionModel,
    selectedInterval,
    selectedStrategy,
    selectedSymbol,
    selectedTradeDirection,
    simulationResult,
    strategies,
    strategyDefinition: selectedStrategyDefinition,
    strategyParams,
    summary,
    tradeDirectionOptions: TRADE_DIRECTION_OPTIONS,
    builderActions: {
      ...strategyBuilderActions,
      loadPresetIntoBuilder
    },
    runOptimization,
    runSimulation,
    setAnalysisMode,
    setOptimizationMetric,
    setSelectedExecutionModel,
    setSelectedInterval,
    setSelectedStrategy,
    setSelectedSymbol,
    setSelectedTradeDirection,
    templateOptions: STRATEGY_BUILDER_TEMPLATES,
    updateAssumption,
    updateExecutionParam,
    updateOptimizationParam,
    updateRange,
    updateStrategyParam,
    validationMessage
  };
}
