const BASE_INPUT_OPTIONS = [
  { value: "close", label: "Close Price" },
  { value: "open", label: "Open Price" },
  { value: "high", label: "High Price" },
  { value: "low", label: "Low Price" },
  { value: "volume", label: "Volume" }
];

export const RULE_OPERATOR_OPTIONS = [
  { value: "GREATER_THAN", label: "Greater Than" },
  { value: "LESS_THAN", label: "Less Than" },
  { value: "EQUAL_TO", label: "Equal To" },
  { value: "CROSS_ABOVE", label: "Crosses Above" },
  { value: "CROSS_BELOW", label: "Crosses Below" },
  { value: "IS_BETWEEN", label: "Is Between" },
  { value: "INCREASED_BY_PCT", label: "Has Risen By %" },
  { value: "NBAR_HIGH", label: "Is N-Bar High" },
  { value: "NBAR_LOW", label: "Is N-Bar Low" }
];

// Operators whose right side is always a value (no indicator comparison mode)
export const VALUE_ONLY_OPERATORS = new Set(["IS_BETWEEN", "INCREASED_BY_PCT", "NBAR_HIGH", "NBAR_LOW"]);

// Operators that need a second threshold (rightValue2)
export const DUAL_VALUE_OPERATORS = new Set(["IS_BETWEEN", "INCREASED_BY_PCT"]);

export const RULE_GROUP_OPTIONS = [
  { value: "AND", label: "AND" },
  { value: "OR", label: "OR" }
];

const TYPE_ALIASES = {
  MA: "SMA",
  SMA: "SMA",
  EMA: "EMA",
  WMA: "WMA",
  DEMA: "DEMA",
  DOUBLE_EMA: "DEMA",
  TEMA: "TEMA",
  TRIPLE_EMA: "TEMA",
  KAMA: "KAMA",
  HMA: "HMA",
  HULL_MOVING_AVERAGE: "HMA",
  RSI: "RSI",
  STOCHASTIC_K: "STOCHASTIC_K",
  STOCHASTIC_OSCILLATOR_K: "STOCHASTIC_K",
  STOCHASTIC_D: "STOCHASTIC_D",
  STOCHASTIC_OSCILLATOR_D: "STOCHASTIC_D",
  WILLIAMS_R: "WILLIAMS_R",
  ROC: "ROC",
  MOMENTUM: "MOMENTUM",
  CCI: "CCI",
  BOLLINGER: "BOLLINGER",
  BOLLINGER_BANDS: "BOLLINGER",
  ATR: "ATR",
  STANDARD_DEVIATION: "STANDARD_DEVIATION",
  STDDEV: "STANDARD_DEVIATION",
  MACD: "MACD",
  PPO: "PPO",
  VWAP: "VWAP",
  OBV: "OBV",
  ON_BALANCE_VOLUME: "OBV",
  ACCUMULATION_DISTRIBUTION: "ACCUMULATION_DISTRIBUTION",
  AD: "ACCUMULATION_DISTRIBUTION",
  DIFFERENCE: "DIFFERENCE",
  RATIO: "RATIO",
  TRANSFORM: "TRANSFORM",
  LAG: "LAG"
};

const INDICATOR_STYLE_DEFAULTS = {
  default: {
    color: "#78d2ff",
    lineWidth: 2
  },
  BOLLINGER: {
    color: "#78d2ff",
    bandColor: "#f7c75f",
    lineWidth: 2
  },
  VWAP: {
    color: "#f4b860",
    lineWidth: 2
  }
};

const indicatorIdCounters = new Map();
let nodeCounter = 0;

function categoryLabel(category) {
  return category.charAt(0) + category.slice(1).toLowerCase();
}

function numericField(name, label, defaultValue, overrides = {}) {
  return {
    name,
    label,
    type: "number",
    defaultValue,
    step: overrides.step ?? 1,
    tooltip: overrides.tooltip ?? "",
    min: overrides.min,
    max: overrides.max,
    optimizable: overrides.optimizable ?? true,
    visibleWhen: overrides.visibleWhen
  };
}

function selectField(name, label, defaultValue, options, overrides = {}) {
  return {
    name,
    label,
    type: "select",
    defaultValue,
    options,
    tooltip: overrides.tooltip ?? "",
    optimizable: false,
    visibleWhen: overrides.visibleWhen
  };
}

function referenceField(name, label, overrides = {}) {
  return {
    name,
    label,
    type: "reference",
    tooltip: overrides.tooltip ?? "",
    optimizable: false,
    allowBaseInputs: overrides.allowBaseInputs ?? true,
    visibleWhen: overrides.visibleWhen
  };
}

function defineIndicator(config) {
  return {
    ...config,
    value: config.value,
    chartSeries: config.chartSeries ?? [{ key: "", label: config.label, band: false }],
    referenceOutputs: config.referenceOutputs ?? [{ key: "", label: config.label }],
    params: config.params ?? []
  };
}

export const INDICATOR_LIBRARY = {
  SMA: defineIndicator({
    value: "SMA",
    label: "Simple Moving Average",
    shortLabel: "SMA",
    category: "TREND",
    description: "Smooths the selected input over a rolling window.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "sma",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 20, {
        min: 1,
        max: 500,
        tooltip: "Number of bars used in the average."
      })
    ]
  }),
  EMA: defineIndicator({
    value: "EMA",
    label: "Exponential Moving Average",
    shortLabel: "EMA",
    category: "TREND",
    description: "A faster average that weights recent candles more heavily.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "ema",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 20, {
        min: 1,
        max: 500,
        tooltip: "Number of bars used in the EMA."
      })
    ]
  }),
  WMA: defineIndicator({
    value: "WMA",
    label: "Weighted Moving Average",
    shortLabel: "WMA",
    category: "TREND",
    description: "Weights the latest values linearly across the lookback window.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "wma",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 20, {
        min: 1,
        max: 500,
        tooltip: "Number of bars used in the WMA."
      })
    ]
  }),
  DEMA: defineIndicator({
    value: "DEMA",
    label: "Double EMA",
    shortLabel: "DEMA",
    category: "TREND",
    description: "A reduced-lag moving average built from two EMA passes.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "dema",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 20, {
        min: 1,
        max: 500,
        tooltip: "Bars used to calculate the DEMA."
      })
    ]
  }),
  TEMA: defineIndicator({
    value: "TEMA",
    label: "Triple EMA",
    shortLabel: "TEMA",
    category: "TREND",
    description: "A three-stage EMA designed to cut lag further.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "tema",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 20, {
        min: 1,
        max: 500,
        tooltip: "Bars used to calculate the TEMA."
      })
    ]
  }),
  KAMA: defineIndicator({
    value: "KAMA",
    label: "Kaufman Adaptive MA",
    shortLabel: "KAMA",
    category: "TREND",
    description: "Adapts smoothing based on price efficiency and volatility.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "kama",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 10, {
        min: 1,
        max: 200,
        tooltip: "Efficiency lookback period."
      }),
      numericField("fastPeriod", "Fast", 2, {
        min: 1,
        max: 50,
        tooltip: "Fast smoothing period."
      }),
      numericField("slowPeriod", "Slow", 30, {
        min: 2,
        max: 200,
        tooltip: "Slow smoothing period."
      })
    ]
  }),
  HMA: defineIndicator({
    value: "HMA",
    label: "Hull Moving Average",
    shortLabel: "HMA",
    category: "TREND",
    description: "Combines WMAs to create a smoother and faster trend line.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "hma",
    chartOverlay: true,
    params: [
      numericField("window", "Window", 20, {
        min: 2,
        max: 500,
        tooltip: "Bars used to calculate the HMA."
      })
    ]
  }),
  RSI: defineIndicator({
    value: "RSI",
    label: "Relative Strength Index",
    shortLabel: "RSI",
    category: "MOMENTUM",
    description: "Measures momentum by comparing recent gains and losses.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "rsi",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 14, {
        min: 2,
        max: 200,
        tooltip: "Lookback period used in the RSI calculation."
      })
    ]
  }),
  STOCHASTIC_K: defineIndicator({
    value: "STOCHASTIC_K",
    label: "Stochastic %K",
    shortLabel: "Stoch %K",
    category: "MOMENTUM",
    description: "Compares closing price to the recent high-low range.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "stochK",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 14, {
        min: 2,
        max: 200,
        tooltip: "Bars used to measure the trading range."
      }),
      referenceField("highInput", "High Input", {
        tooltip: "Series used for the high range. Defaults to the high price."
      }),
      referenceField("lowInput", "Low Input", {
        tooltip: "Series used for the low range. Defaults to the low price."
      })
    ]
  }),
  STOCHASTIC_D: defineIndicator({
    value: "STOCHASTIC_D",
    label: "Stochastic %D",
    shortLabel: "Stoch %D",
    category: "MOMENTUM",
    description: "Applies smoothing to the %K stochastic line.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "stochD",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 14, {
        min: 2,
        max: 200,
        tooltip: "Bars used to measure the trading range."
      }),
      referenceField("highInput", "High Input", {
        tooltip: "Series used for the high range. Defaults to the high price."
      }),
      referenceField("lowInput", "Low Input", {
        tooltip: "Series used for the low range. Defaults to the low price."
      })
    ]
  }),
  WILLIAMS_R: defineIndicator({
    value: "WILLIAMS_R",
    label: "Williams %R",
    shortLabel: "Williams %R",
    category: "MOMENTUM",
    description: "Shows where close sits inside the recent high-low range.",
    supportsInput: false,
    defaultId: "williamsR",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 14, {
        min: 2,
        max: 200,
        tooltip: "Bars used to measure the recent range."
      })
    ]
  }),
  ROC: defineIndicator({
    value: "ROC",
    label: "Rate Of Change",
    shortLabel: "ROC",
    category: "MOMENTUM",
    description: "Measures percentage change from a prior bar.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "roc",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 12, {
        min: 1,
        max: 200,
        tooltip: "Bars back used for the comparison."
      })
    ]
  }),
  MOMENTUM: defineIndicator({
    value: "MOMENTUM",
    label: "Momentum",
    shortLabel: "Momentum",
    category: "MOMENTUM",
    description: "Measures the raw difference from a prior bar.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "momentum",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 10, {
        min: 1,
        max: 200,
        tooltip: "Bars back used for the difference calculation."
      })
    ]
  }),
  CCI: defineIndicator({
    value: "CCI",
    label: "Commodity Channel Index",
    shortLabel: "CCI",
    category: "MOMENTUM",
    description: "Compares typical price against its mean deviation.",
    supportsInput: false,
    defaultId: "cci",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 20, {
        min: 2,
        max: 200,
        tooltip: "Bars used in the CCI calculation."
      })
    ]
  }),
  BOLLINGER: defineIndicator({
    value: "BOLLINGER",
    label: "Bollinger Bands",
    shortLabel: "Bollinger",
    category: "VOLATILITY",
    description: "Builds a middle band with upper and lower deviation envelopes.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "bb",
    chartOverlay: true,
    referenceOutputs: [
      { key: "", label: "Middle Band" },
      { key: ".upper", label: "Upper Band" },
      { key: ".lower", label: "Lower Band" }
    ],
    chartSeries: [
      { key: "", label: "Middle Band", band: false },
      { key: ".upper", label: "Upper Band", band: true },
      { key: ".lower", label: "Lower Band", band: true }
    ],
    params: [
      numericField("window", "Window", 20, {
        min: 2,
        max: 300,
        tooltip: "Bars used for the moving average and deviation band."
      }),
      numericField("stdDevMultiplier", "Std Dev", 2, {
        min: 0.25,
        max: 5,
        step: 0.25,
        tooltip: "Multiplier applied to the standard deviation."
      })
    ]
  }),
  ATR: defineIndicator({
    value: "ATR",
    label: "Average True Range",
    shortLabel: "ATR",
    category: "VOLATILITY",
    description: "Tracks average trading range over a rolling window.",
    supportsInput: false,
    defaultId: "atr",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 14, {
        min: 1,
        max: 200,
        tooltip: "Bars used to average true range."
      })
    ]
  }),
  STANDARD_DEVIATION: defineIndicator({
    value: "STANDARD_DEVIATION",
    label: "Standard Deviation",
    shortLabel: "Std Dev",
    category: "VOLATILITY",
    description: "Measures the spread of the selected input around its mean.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "stddev",
    chartOverlay: false,
    params: [
      numericField("period", "Period", 20, {
        min: 2,
        max: 200,
        tooltip: "Bars used in the deviation calculation."
      })
    ]
  }),
  MACD: defineIndicator({
    value: "MACD",
    label: "MACD",
    shortLabel: "MACD",
    category: "HYBRID",
    description: "Tracks the spread between fast and slow EMAs and exposes signal lines.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "macd",
    chartOverlay: false,
    referenceOutputs: [
      { key: "", label: "MACD" },
      { key: ".signal", label: "Signal" },
      { key: ".histogram", label: "Histogram" }
    ],
    params: [
      numericField("shortPeriod", "Fast", 12, {
        min: 1,
        max: 200,
        tooltip: "Fast EMA period."
      }),
      numericField("longPeriod", "Slow", 26, {
        min: 2,
        max: 300,
        tooltip: "Slow EMA period."
      }),
      numericField("signalPeriod", "Signal", 9, {
        min: 1,
        max: 100,
        tooltip: "Signal smoothing period."
      })
    ]
  }),
  PPO: defineIndicator({
    value: "PPO",
    label: "Percentage Price Oscillator",
    shortLabel: "PPO",
    category: "HYBRID",
    description: "Normalizes the EMA spread as a percentage of the slow EMA.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "ppo",
    chartOverlay: false,
    params: [
      numericField("shortPeriod", "Fast", 12, {
        min: 1,
        max: 200,
        tooltip: "Fast EMA period."
      }),
      numericField("longPeriod", "Slow", 26, {
        min: 2,
        max: 300,
        tooltip: "Slow EMA period."
      })
    ]
  }),
  VWAP: defineIndicator({
    value: "VWAP",
    label: "VWAP",
    shortLabel: "VWAP",
    category: "VOLUME",
    description: "Tracks the average price weighted by volume across a rolling window.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "vwap",
    chartOverlay: true,
    params: [
      numericField("period", "Period", 14, {
        min: 1,
        max: 200,
        tooltip: "Bars used in the VWAP rolling window."
      }),
      referenceField("volumeInput", "Volume Input", {
        tooltip: "Volume series used in the VWAP calculation."
      })
    ]
  }),
  OBV: defineIndicator({
    value: "OBV",
    label: "On Balance Volume",
    shortLabel: "OBV",
    category: "VOLUME",
    description: "Cumulative volume adjusted by close-to-close direction.",
    supportsInput: false,
    defaultId: "obv",
    chartOverlay: false
  }),
  ACCUMULATION_DISTRIBUTION: defineIndicator({
    value: "ACCUMULATION_DISTRIBUTION",
    label: "Accumulation Distribution",
    shortLabel: "A/D",
    category: "VOLUME",
    description: "Measures money flow using close location and volume.",
    supportsInput: false,
    defaultId: "ad",
    chartOverlay: false
  }),
  DIFFERENCE: defineIndicator({
    value: "DIFFERENCE",
    label: "Difference",
    shortLabel: "Diff",
    category: "UTILITY",
    description: "Subtracts either another series or a lagged version of the input.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "diff",
    chartOverlay: false,
    params: [
      referenceField("rightInput", "Right Input", {
        tooltip: "Optional comparison series. Leave blank to use a lagged input."
      }),
      numericField("lag", "Lag", 1, {
        min: 1,
        max: 100,
        tooltip: "Used when no right input is selected.",
        visibleWhen: (params) => !params.rightInput
      })
    ]
  }),
  RATIO: defineIndicator({
    value: "RATIO",
    label: "Ratio",
    shortLabel: "Ratio",
    category: "UTILITY",
    description: "Divides the primary input by another indicator or base series.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "ratio",
    chartOverlay: false,
    params: [
      referenceField("rightInput", "Right Input", {
        tooltip: "Comparison series used as the denominator."
      })
    ]
  }),
  TRANSFORM: defineIndicator({
    value: "TRANSFORM",
    label: "Transform",
    shortLabel: "Transform",
    category: "UTILITY",
    description: "Applies a mathematical transform to the selected input.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "transform",
    chartOverlay: false,
    params: [
      selectField("operation", "Operation", "ABS", [
        { value: "ABS", label: "Absolute" },
        { value: "SQRT", label: "Square Root" },
        { value: "SQUARE", label: "Square" },
        { value: "SMA", label: "SMA" },
        { value: "EMA", label: "EMA" },
        { value: "STDDEV", label: "Std Dev" },
        { value: "HIGHEST", label: "Highest" },
        { value: "LOWEST", label: "Lowest" },
        { value: "ADD", label: "Add" },
        { value: "SUBTRACT", label: "Subtract" },
        { value: "MULTIPLY", label: "Multiply" },
        { value: "DIVIDE", label: "Divide" }
      ], {
        tooltip: "Transform applied to the input series."
      }),
      numericField("window", "Window", 14, {
        min: 1,
        max: 200,
        tooltip: "Required for rolling-window operations.",
        visibleWhen: (params) => ["SMA", "EMA", "STDDEV", "HIGHEST", "LOWEST"].includes(params.operation)
      }),
      referenceField("rightInput", "Right Input", {
        tooltip: "Optional indicator operand for binary operations.",
        visibleWhen: (params) => ["ADD", "SUBTRACT", "MULTIPLY", "DIVIDE"].includes(params.operation)
      }),
      numericField("value", "Value", 1, {
        min: -1000000,
        max: 1000000,
        step: 0.1,
        tooltip: "Fallback constant when no right input is selected for binary operations.",
        visibleWhen: (params) => ["ADD", "SUBTRACT", "MULTIPLY", "DIVIDE"].includes(params.operation)
      })
    ]
  }),
  LAG: defineIndicator({
    value: "LAG",
    label: "Lag",
    shortLabel: "Lag",
    category: "UTILITY",
    description: "Shifts the selected input back by a fixed number of bars.",
    supportsInput: true,
    defaultInput: "close",
    defaultId: "lag",
    chartOverlay: false,
    params: [
      numericField("lag", "Lag", 1, {
        min: 1,
        max: 200,
        tooltip: "Number of prior bars to reference."
      })
    ]
  })
};

export const STRATEGY_BUILDER_TEMPLATES = [
  {
    value: "CUSTOM",
    label: "Custom DSL",
    description: "Start from a blank strategy and build everything visually."
  },
  {
    value: "BUY_AND_HOLD",
    label: "Buy And Hold",
    description: "Enter once and hold until the end of the replay."
  },
  {
    value: "MA_CROSSOVER",
    label: "MA Crossover",
    description: "Bullish crossover entry with bearish crossover exit."
  },
  {
    value: "RSI",
    label: "RSI Reversal",
    description: "Uses RSI oversold and overbought thresholds."
  },
  {
    value: "BOLLINGER_BANDS",
    label: "Bollinger Bands",
    description: "Uses lower and upper band recovery and rejection."
  }
];

function normalizeType(type) {
  const normalized = String(type ?? "").trim().toUpperCase().replace(/\s+/g, "_");
  return TYPE_ALIASES[normalized] ?? normalized;
}

function getIndicatorDefinition(type) {
  return INDICATOR_LIBRARY[normalizeType(type)] ?? INDICATOR_LIBRARY.SMA;
}

function createNodeId(prefix) {
  nodeCounter += 1;
  return `${prefix}_${nodeCounter}`;
}

function sanitizeId(id, fallback) {
  const value = String(id ?? "").trim().replace(/\s+/g, "");
  return value || fallback;
}

function nextIndicatorId(baseId) {
  const current = indicatorIdCounters.get(baseId) ?? 0;
  indicatorIdCounters.set(baseId, current + 1);
  return current === 0 ? baseId : `${baseId}${current + 1}`;
}

function isFiniteNumber(value) {
  return Number.isFinite(Number(value));
}

function toNumber(value, fallback = 0) {
  return isFiniteNumber(value) ? Number(value) : fallback;
}

function cloneValue(value) {
  if (Array.isArray(value)) {
    return value.map(cloneValue);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, cloneValue(entry)]));
  }
  return value;
}

function createIndicatorStyle(type, style = {}) {
  const defaults = INDICATOR_STYLE_DEFAULTS[normalizeType(type)] ?? INDICATOR_STYLE_DEFAULTS.default;
  return {
    ...defaults,
    ...style
  };
}

function buildDefaultParams(definition) {
  return Object.fromEntries(definition.params.map((field) => [field.name, cloneValue(field.defaultValue)]));
}

function coerceFieldValue(field, value) {
  if (field.type === "number") {
    return toNumber(value, field.defaultValue ?? 0);
  }
  if (field.type === "select") {
    return value == null ? field.defaultValue : String(value);
  }
  if (field.type === "reference") {
    return value == null ? "" : value;
  }
  return value;
}

// Common aliases for indicator param names coming from external DSL JSON.
// Keys are the frontend/backend canonical names; values are accepted synonyms.
// This mirrors the alias order used by IndicatorUtils.getInt on the backend.
const PARAM_ALIASES = {
  window:          ["period", "barCount", "length"],
  period:          ["window", "barCount", "length"],
  barCount:        ["window", "period", "length"],
  fastPeriod:      ["fast", "shortPeriod", "shortWindow", "fastWindow"],
  slowPeriod:      ["slow", "longPeriod",  "longWindow",  "slowWindow"],
  signalPeriod:    ["signal", "signalWindow"],
  stdDevMultiplier:["multiplier", "stdDev", "deviation", "factor"],
  multiplier:      ["stdDevMultiplier", "factor"],
};

function resolveParamValue(field, params) {
  if (params[field.name] !== undefined) {
    return params[field.name];
  }
  const aliases = PARAM_ALIASES[field.name];
  if (aliases) {
    for (const alias of aliases) {
      if (params[alias] !== undefined) {
        return params[alias];
      }
    }
  }
  return undefined;
}

function normalizeParams(definition, params = {}) {
  const next = buildDefaultParams(definition);
  definition.params.forEach((field) => {
    const value = resolveParamValue(field, params);
    if (value !== undefined) {
      next[field.name] = coerceFieldValue(field, value);
    }
  });
  return next;
}

function normalizeInput(definition, indicator) {
  const legacyInput = indicator?.params?.input;
  const input = indicator?.input ?? legacyInput;
  if (!definition.supportsInput) {
    return null;
  }
  if (input == null || input === "") {
    return definition.defaultInput ?? "close";
  }
  if (typeof input === "string") {
    return input;
  }
  return normalizeIndicator(input);
}

function getVisibleFields(definition, indicator) {
  return definition.params.filter((field) => (
    typeof field.visibleWhen === "function" ? field.visibleWhen(indicator.params ?? {}) : true
  ));
}

function getReferenceOutputs(indicator) {
  const definition = getIndicatorDefinition(indicator.type);
  return definition.referenceOutputs.map((output) => ({
    value: `${indicator.id}${output.key}`,
    label: output.key ? `${indicator.id} ${output.label}` : indicator.id
  }));
}

function registerCacheEntry(cache, key, values) {
  if (key) {
    cache[key] = values;
  }
}

function mapWindow(values, window, calculator) {
  return values.map((_, index) => {
    if (index < window - 1) {
      return null;
    }
    return calculator(values.slice(index - window + 1, index + 1), index);
  });
}

function sma(values, window) {
  const divisor = Math.max(1, Math.floor(window));
  return mapWindow(values, divisor, (slice) => slice.reduce((sum, value) => sum + value, 0) / divisor);
}

function ema(values, window) {
  const period = Math.max(1, Math.floor(window));
  const multiplier = 2 / (period + 1);
  const result = [];
  values.forEach((value, index) => {
    if (index === 0) {
      result.push(value);
      return;
    }
    result.push(value * multiplier + result[index - 1] * (1 - multiplier));
  });
  return result.map((value) => (Number.isFinite(value) ? value : null));
}

function wma(values, window) {
  const period = Math.max(1, Math.floor(window));
  const denominator = (period * (period + 1)) / 2;
  return mapWindow(values, period, (slice) => (
    slice.reduce((sum, value, index) => sum + value * (index + 1), 0) / denominator
  ));
}

function difference(values, lag = 1) {
  const offset = Math.max(1, Math.floor(lag));
  return values.map((value, index) => (index >= offset ? value - values[index - offset] : null));
}

function standardDeviation(values, window) {
  const period = Math.max(1, Math.floor(window));
  return mapWindow(values, period, (slice) => {
    const average = slice.reduce((sum, value) => sum + value, 0) / period;
    const variance = slice.reduce((sum, value) => sum + ((value - average) ** 2), 0) / period;
    return Math.sqrt(variance);
  });
}

function highest(values, window) {
  const period = Math.max(1, Math.floor(window));
  return mapWindow(values, period, (slice) => Math.max(...slice));
}

function lowest(values, window) {
  const period = Math.max(1, Math.floor(window));
  return mapWindow(values, period, (slice) => Math.min(...slice));
}

function dema(values, window) {
  const ema1 = ema([...values], window);
  const ema2 = ema(ema1.map((value) => value ?? 0), window);
  return ema1.map((value, index) => (
    value == null || ema2[index] == null ? null : (2 * value) - ema2[index]
  ));
}

function tema(values, window) {
  const ema1 = ema([...values], window);
  const ema2 = ema(ema1.map((value) => value ?? 0), window);
  const ema3 = ema(ema2.map((value) => value ?? 0), window);
  return ema1.map((value, index) => (
    value == null || ema2[index] == null || ema3[index] == null
      ? null
      : (3 * value) - (3 * ema2[index]) + ema3[index]
  ));
}

function hma(values, window) {
  const period = Math.max(2, Math.floor(window));
  const half = Math.max(1, Math.floor(period / 2));
  const root = Math.max(1, Math.floor(Math.sqrt(period)));
  const wmaHalf = wma(values, half);
  const wmaFull = wma(values, period);
  const raw = values.map((_, index) => (
    wmaHalf[index] == null || wmaFull[index] == null ? null : (2 * wmaHalf[index]) - wmaFull[index]
  ));
  return wma(raw.map((value) => value ?? 0), root).map((value, index) => (raw[index] == null ? null : value));
}

function kama(values, window, fastPeriod, slowPeriod) {
  const period = Math.max(1, Math.floor(window));
  const fast = 2 / (Math.max(1, fastPeriod) + 1);
  const slow = 2 / (Math.max(1, slowPeriod) + 1);
  const result = [];

  values.forEach((value, index) => {
    if (index < period) {
      result.push(value);
      return;
    }
    const change = Math.abs(value - values[index - period]);
    let volatility = 0;
    for (let cursor = index - period + 1; cursor <= index; cursor += 1) {
      volatility += Math.abs(values[cursor] - values[cursor - 1]);
    }
    const efficiency = volatility === 0 ? 0 : change / volatility;
    const smoothing = (efficiency * (fast - slow) + slow) ** 2;
    const previous = result[index - 1] ?? values[index - 1];
    result.push(previous + smoothing * (value - previous));
  });

  return result;
}

function rsi(values, period) {
  const window = Math.max(1, Math.floor(period));
  const result = values.map(() => null);
  let gains = 0;
  let losses = 0;

  for (let index = 1; index <= window && index < values.length; index += 1) {
    const delta = values[index] - values[index - 1];
    gains += Math.max(delta, 0);
    losses += Math.max(-delta, 0);
  }

  if (values.length <= window) {
    return result;
  }

  let averageGain = gains / window;
  let averageLoss = losses / window;
  result[window] = averageLoss === 0 ? 100 : 100 - (100 / (1 + (averageGain / averageLoss)));

  for (let index = window + 1; index < values.length; index += 1) {
    const delta = values[index] - values[index - 1];
    averageGain = ((averageGain * (window - 1)) + Math.max(delta, 0)) / window;
    averageLoss = ((averageLoss * (window - 1)) + Math.max(-delta, 0)) / window;
    result[index] = averageLoss === 0 ? 100 : 100 - (100 / (1 + (averageGain / averageLoss)));
  }

  return result;
}

function atr(highs, lows, closes, period) {
  const trueRange = highs.map((high, index) => {
    if (index === 0) {
      return high - lows[index];
    }
    return Math.max(
      high - lows[index],
      Math.abs(high - closes[index - 1]),
      Math.abs(lows[index] - closes[index - 1])
    );
  });
  return sma(trueRange, period);
}

function cci(highs, lows, closes, period) {
  const typicalPrice = closes.map((close, index) => (highs[index] + lows[index] + close) / 3);
  const tpSma = sma(typicalPrice, period);
  return typicalPrice.map((value, index) => {
    if (index < period - 1 || tpSma[index] == null) {
      return null;
    }
    const slice = typicalPrice.slice(index - period + 1, index + 1);
    const meanDeviation = slice.reduce((sum, entry) => sum + Math.abs(entry - tpSma[index]), 0) / period;
    return meanDeviation === 0 ? 0 : (value - tpSma[index]) / (0.015 * meanDeviation);
  });
}

function rollingVwap(prices, volumes, period) {
  const window = Math.max(1, Math.floor(period));
  return prices.map((_, index) => {
    if (index < window - 1) {
      return null;
    }
    let priceVolume = 0;
    let volumeTotal = 0;
    for (let cursor = index - window + 1; cursor <= index; cursor += 1) {
      priceVolume += prices[cursor] * volumes[cursor];
      volumeTotal += volumes[cursor];
    }
    return volumeTotal === 0 ? null : priceVolume / volumeTotal;
  });
}

function obv(closes, volumes) {
  const result = [];
  let total = 0;
  closes.forEach((close, index) => {
    if (index === 0) {
      result.push(0);
      return;
    }
    if (close > closes[index - 1]) {
      total += volumes[index];
    } else if (close < closes[index - 1]) {
      total -= volumes[index];
    }
    result.push(total);
  });
  return result;
}

function accumulationDistribution(highs, lows, closes, volumes) {
  const result = [];
  let total = 0;
  closes.forEach((close, index) => {
    const range = highs[index] - lows[index];
    const multiplier = range === 0 ? 0 : (((close - lows[index]) - (highs[index] - close)) / range);
    total += multiplier * volumes[index];
    result.push(total);
  });
  return result;
}

function resolveBinaryOperand(cache, indicatorsById, candles, definition, paramName, fallback = null) {
  const param = definition.params?.[paramName];
  if (param == null || param === "") {
    return fallback;
  }
  return resolveSeries(cache, indicatorsById, candles, param);
}

function buildSeries(cache, indicatorsById, candles, indicator) {
  const normalized = normalizeIndicator(indicator);
  if (cache[normalized.id]) {
    return cache[normalized.id];
  }

  const definition = getIndicatorDefinition(normalized.type);
  const prices = {
    open: candles.map((candle) => Number(candle.open)),
    high: candles.map((candle) => Number(candle.high)),
    low: candles.map((candle) => Number(candle.low)),
    close: candles.map((candle) => Number(candle.close)),
    volume: candles.map((candle) => Number(candle.volume))
  };
  const input = definition.supportsInput
    ? resolveSeries(cache, indicatorsById, candles, normalized.input ?? definition.defaultInput ?? "close")
    : null;
  let primary = input ?? prices.close;
  const extras = {};

  switch (normalized.type) {
    case "SMA":
      primary = sma(input, normalized.params.window);
      break;
    case "EMA":
      primary = ema([...input], normalized.params.window);
      break;
    case "WMA":
      primary = wma(input, normalized.params.window);
      break;
    case "DEMA":
      primary = dema(input, normalized.params.window);
      break;
    case "TEMA":
      primary = tema(input, normalized.params.window);
      break;
    case "KAMA":
      primary = kama(input, normalized.params.window, normalized.params.fastPeriod, normalized.params.slowPeriod);
      break;
    case "HMA":
      primary = hma(input, normalized.params.window);
      break;
    case "RSI":
      primary = rsi(input, normalized.params.period);
      break;
    case "STOCHASTIC_K": {
      const highInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "highInput", prices.high);
      const lowInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "lowInput", prices.low);
      const highestHigh = highest(highInput, normalized.params.period);
      const lowestLow = lowest(lowInput, normalized.params.period);
      primary = input.map((value, index) => {
        if (highestHigh[index] == null || lowestLow[index] == null) {
          return null;
        }
        const range = highestHigh[index] - lowestLow[index];
        return range === 0 ? 0 : ((value - lowestLow[index]) / range) * 100;
      });
      break;
    }
    case "STOCHASTIC_D": {
      const highInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "highInput", prices.high);
      const lowInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "lowInput", prices.low);
      const highestHigh = highest(highInput, normalized.params.period);
      const lowestLow = lowest(lowInput, normalized.params.period);
      const k = input.map((value, index) => {
        if (highestHigh[index] == null || lowestLow[index] == null) {
          return null;
        }
        const range = highestHigh[index] - lowestLow[index];
        return range === 0 ? 0 : ((value - lowestLow[index]) / range) * 100;
      });
      primary = sma(k.map((value) => value ?? 0), 3).map((value, index) => (k[index] == null ? null : value));
      break;
    }
    case "WILLIAMS_R": {
      const highestHigh = highest(prices.high, normalized.params.period);
      const lowestLow = lowest(prices.low, normalized.params.period);
      primary = prices.close.map((value, index) => {
        if (highestHigh[index] == null || lowestLow[index] == null) {
          return null;
        }
        const range = highestHigh[index] - lowestLow[index];
        return range === 0 ? 0 : ((highestHigh[index] - value) / range) * -100;
      });
      break;
    }
    case "ROC":
      primary = input.map((value, index) => {
        const source = input[index - normalized.params.period];
        if (index < normalized.params.period || !Number.isFinite(source) || source === 0) {
          return null;
        }
        return ((value - source) / source) * 100;
      });
      break;
    case "MOMENTUM":
      primary = difference(input, normalized.params.period);
      break;
    case "CCI":
      primary = cci(prices.high, prices.low, prices.close, normalized.params.period);
      break;
    case "BOLLINGER": {
      const middle = sma(input, normalized.params.window);
      const deviation = standardDeviation(input, normalized.params.window);
      const upper = middle.map((value, index) => (
        value == null || deviation[index] == null ? null : value + deviation[index] * normalized.params.stdDevMultiplier
      ));
      const lower = middle.map((value, index) => (
        value == null || deviation[index] == null ? null : value - deviation[index] * normalized.params.stdDevMultiplier
      ));
      primary = middle;
      extras[`${normalized.id}.upper`] = upper;
      extras[`${normalized.id}.lower`] = lower;
      extras[`${normalized.id}.middle`] = middle;
      break;
    }
    case "ATR":
      primary = atr(prices.high, prices.low, prices.close, normalized.params.period);
      break;
    case "STANDARD_DEVIATION":
      primary = standardDeviation(input, normalized.params.period);
      break;
    case "MACD": {
      const shortLine = ema([...input], normalized.params.shortPeriod);
      const longLine = ema([...input], normalized.params.longPeriod);
      const macdLine = shortLine.map((value, index) => (
        value == null || longLine[index] == null ? null : value - longLine[index]
      ));
      const signal = ema(macdLine.map((value) => value ?? 0), normalized.params.signalPeriod)
        .map((value, index) => (macdLine[index] == null ? null : value));
      const histogram = macdLine.map((value, index) => (
        value == null || signal[index] == null ? null : value - signal[index]
      ));
      primary = macdLine;
      extras[`${normalized.id}.signal`] = signal;
      extras[`${normalized.id}.histogram`] = histogram;
      break;
    }
    case "PPO": {
      const shortLine = ema([...input], normalized.params.shortPeriod);
      const longLine = ema([...input], normalized.params.longPeriod);
      primary = shortLine.map((value, index) => {
        if (value == null || longLine[index] == null || longLine[index] === 0) {
          return null;
        }
        return ((value - longLine[index]) / longLine[index]) * 100;
      });
      break;
    }
    case "VWAP": {
      const volumeInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "volumeInput", prices.volume);
      primary = rollingVwap(input, volumeInput, normalized.params.period);
      break;
    }
    case "OBV":
      primary = obv(prices.close, prices.volume);
      break;
    case "ACCUMULATION_DISTRIBUTION":
      primary = accumulationDistribution(prices.high, prices.low, prices.close, prices.volume);
      break;
    case "DIFFERENCE": {
      const rightInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "rightInput", null);
      primary = rightInput
        ? input.map((value, index) => (rightInput[index] == null ? null : value - rightInput[index]))
        : difference(input, normalized.params.lag);
      break;
    }
    case "RATIO": {
      const rightInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "rightInput", null);
      primary = input.map((value, index) => {
        const divisor = rightInput?.[index];
        return !Number.isFinite(divisor) || divisor === 0 ? null : value / divisor;
      });
      break;
    }
    case "TRANSFORM": {
      const operation = String(normalized.params.operation ?? "ABS").toUpperCase();
      const rightInput = resolveBinaryOperand(cache, indicatorsById, candles, normalized, "rightInput", null);
      const constant = normalized.params.value;
      if (operation === "ABS") {
        primary = input.map((value) => Math.abs(value));
      } else if (operation === "SQRT") {
        primary = input.map((value) => (value < 0 ? null : Math.sqrt(value)));
      } else if (operation === "SQUARE") {
        primary = input.map((value) => value ** 2);
      } else if (operation === "SMA") {
        primary = sma(input, normalized.params.window);
      } else if (operation === "EMA") {
        primary = ema([...input], normalized.params.window);
      } else if (operation === "STDDEV") {
        primary = standardDeviation(input, normalized.params.window);
      } else if (operation === "HIGHEST") {
        primary = highest(input, normalized.params.window);
      } else if (operation === "LOWEST") {
        primary = lowest(input, normalized.params.window);
      } else if (["ADD", "SUBTRACT", "MULTIPLY", "DIVIDE"].includes(operation)) {
        primary = input.map((value, index) => {
          const operand = rightInput?.[index] ?? constant;
          if (!Number.isFinite(operand)) {
            return null;
          }
          if (operation === "ADD") {
            return value + operand;
          }
          if (operation === "SUBTRACT") {
            return value - operand;
          }
          if (operation === "MULTIPLY") {
            return value * operand;
          }
          return operand === 0 ? null : value / operand;
        });
      }
      break;
    }
    case "LAG":
      primary = input.map((_, index) => (index >= normalized.params.lag ? input[index - normalized.params.lag] : null));
      break;
    default:
      primary = input ?? prices.close;
      break;
  }

  registerCacheEntry(cache, normalized.id, primary);
  Object.entries(extras).forEach(([key, values]) => registerCacheEntry(cache, key, values));
  return primary;
}

function resolveSeries(cache, indicatorsById, candles, input) {
  if (input == null || input === "") {
    return cache.close;
  }
  if (typeof input === "string") {
    if (cache[input]) {
      return cache[input];
    }
    const rootId = input.split(".")[0];
    if (indicatorsById[rootId]) {
      buildSeries(cache, indicatorsById, candles, indicatorsById[rootId]);
      return cache[input] ?? cache[rootId] ?? cache.close;
    }
    return cache[input] ?? cache.close;
  }
  const nested = normalizeIndicator(input);
  return buildSeries(cache, indicatorsById, candles, nested);
}

export function createIndicator(type, overrides = {}) {
  const definition = getIndicatorDefinition(type);
  const fallbackId = nextIndicatorId(definition.defaultId ?? definition.value.toLowerCase());
  return {
    id: sanitizeId(overrides.id, fallbackId),
    type: definition.value,
    subType: overrides.subType ?? definition.defaultSubType ?? null,
    params: normalizeParams(definition, overrides.params),
    input: overrides.input !== undefined ? cloneValue(overrides.input) : (definition.supportsInput ? definition.defaultInput ?? "close" : null),
    style: createIndicatorStyle(definition.value, overrides.style)
  };
}

export function normalizeIndicator(indicator) {
  if (!indicator || typeof indicator !== "object") {
    return createIndicator("SMA");
  }
  const definition = getIndicatorDefinition(indicator.type);
  const existingId = sanitizeId(indicator.id, "");
  return {
    id: existingId || nextIndicatorId(definition.defaultId ?? definition.value.toLowerCase()),
    type: definition.value,
    subType: indicator.subType ?? definition.defaultSubType ?? null,
    params: normalizeParams(definition, indicator.params ?? {}),
    input: normalizeInput(definition, indicator),
    style: createIndicatorStyle(definition.value, indicator.style)
  };
}

export function createCondition(overrides = {}) {
  return {
    id: overrides.id ?? createNodeId("condition"),
    kind: "condition",
    left: overrides.left ?? "close",
    operator: overrides.operator ?? "GREATER_THAN",
    rightMode: overrides.rightMode ?? (overrides.rightIndicator ? "indicator" : "value"),
    rightIndicator: overrides.rightIndicator ?? "",
    rightValue: overrides.rightValue ?? 0,
    rightValue2: overrides.rightValue2 ?? 0
  };
}

export function createRuleGroup(overrides = {}) {
  const children = Array.isArray(overrides.children) && overrides.children.length
    ? overrides.children.map((child) => cloneRuleNode(child))
    : [createCondition()];

  return {
    id: overrides.id ?? createNodeId("group"),
    kind: "group",
    logicalOperator: overrides.logicalOperator ?? "AND",
    children
  };
}

export function cloneRuleNode(node) {
  if (!node) {
    return createRuleGroup();
  }
  if (node.kind === "group") {
    return createRuleGroup({
      ...node,
      children: (node.children ?? []).map((child) => cloneRuleNode(child))
    });
  }
  return createCondition({ ...node });
}

export function deserializeRuleNode(node) {
  if (!node || typeof node !== "object") {
    return createRuleGroup();
  }

  if (Array.isArray(node.rules) && node.rules.length) {
    return createRuleGroup({
      logicalOperator: node.logicalOperator ?? "AND",
      children: node.rules.map((child) => deserializeRuleNode(child))
    });
  }

  return createCondition({
    left: node.left ?? "close",
    operator: node.operator ?? "GREATER_THAN",
    rightIndicator: node.rightIndicator ?? "",
    rightMode: node.rightIndicator ? "indicator" : "value",
    rightValue: node.rightValue ?? 0,
    rightValue2: node.rightValue2 ?? 0
  });
}

/**
 * Parses a DSL expression string such as "cross_above(sma50, sma200)" or
 * "rsi < 30 AND close > sma50" into a rule-group tree understood by the builder.
 *
 * Supports both function-call form (cross_above(a, b)) and infix form (a cross_above b).
 * Returns a default empty group on blank input or parse failure.
 */
export function parseDslExpression(expression) {
  if (!expression || typeof expression !== "string" || !expression.trim()) {
    return createRuleGroup();
  }

  // ----- tokenizer -----
  function tokenize(expr) {
    const tokens = [];
    let i = 0;
    while (i < expr.length) {
      const c = expr[i];
      if (/\s/.test(c)) { i++; continue; }
      if (c === "(" || c === ")" || c === ",") { tokens.push(c); i++; continue; }
      if ((c === "<" || c === ">" || c === "!" || c === "=") && i + 1 < expr.length && expr[i + 1] === "=") {
        tokens.push(expr.slice(i, i + 2)); i += 2; continue;
      }
      if (c === "<" || c === ">") { tokens.push(c); i++; continue; }
      if (/[a-zA-Z_]/.test(c)) {
        const start = i;
        while (i < expr.length && /[\w.]/.test(expr[i])) i++;
        tokens.push(expr.slice(start, i));
        continue;
      }
      if (/\d/.test(c) || (c === "-" && i + 1 < expr.length && /\d/.test(expr[i + 1]))) {
        const start = i;
        if (c === "-") i++;
        while (i < expr.length && /[\d.]/.test(expr[i])) i++;
        tokens.push(expr.slice(start, i));
        continue;
      }
      i++; // skip unrecognised character
    }
    return tokens;
  }

  const tokens = tokenize(expression.trim());
  let pos = 0;

  function isFuncOp(t) {
    if (!t) return false;
    const l = t.toLowerCase();
    return l === "cross_above" || l === "ca" || l === "cross_below" || l === "cb";
  }

  function mapOperator(op) {
    switch ((op || "").toLowerCase()) {
      case "<": case "<=": return "LESS_THAN";
      case ">": case ">=": return "GREATER_THAN";
      case "cross_above": case "ca": return "CROSS_ABOVE";
      case "cross_below": case "cb": return "CROSS_BELOW";
      default: return "GREATER_THAN";
    }
  }

  function buildCondition(left, op, right) {
    const numeric = right !== undefined && right !== null && !Number.isNaN(Number(right));
    return createCondition({
      left: left || "close",
      operator: mapOperator(op),
      rightMode: numeric ? "value" : "indicator",
      rightValue: numeric ? Number(right) : 0,
      rightIndicator: numeric ? "" : (right || "")
    });
  }

  function parseComparison() {
    const t = tokens[pos];
    // Function-call form: cross_above(left, right)
    if (isFuncOp(t) && pos + 1 < tokens.length && tokens[pos + 1] === "(") {
      const op = tokens[pos++]; pos++; // consume op, then '('
      const left = tokens[pos++];
      if (tokens[pos] === ",") pos++;
      const right = tokens[pos++];
      if (tokens[pos] === ")") pos++;
      return buildCondition(left, op, right);
    }
    // Infix form: left op right
    const left = tokens[pos++];
    const op = tokens[pos++];
    const right = tokens[pos++];
    return buildCondition(left, op, right);
  }

  function parsePrimary() {
    if (pos < tokens.length && tokens[pos] === "(") {
      pos++;
      const inner = parseExpr();
      if (pos < tokens.length && tokens[pos] === ")") pos++;
      return inner;
    }
    return parseComparison();
  }

  function parseAndExpr() {
    const operands = [parsePrimary()];
    while (pos < tokens.length && tokens[pos].toUpperCase() === "AND") {
      pos++;
      operands.push(parsePrimary());
    }
    if (operands.length === 1) return operands[0];
    return createRuleGroup({ logicalOperator: "AND", children: operands });
  }

  function parseExpr() {
    const operands = [parseAndExpr()];
    while (pos < tokens.length && tokens[pos].toUpperCase() === "OR") {
      pos++;
      operands.push(parseAndExpr());
    }
    if (operands.length === 1) return operands[0];
    return createRuleGroup({ logicalOperator: "OR", children: operands });
  }

  try {
    const result = parseExpr();
    // Wrap a bare condition in a group so the builder always sees a group at the top level
    if (result.kind === "condition") {
      return createRuleGroup({ children: [result] });
    }
    return result;
  } catch {
    return createRuleGroup();
  }
}

export function serializeRuleNode(node) {
  if (!node) {
    return null;
  }
  if (node.kind === "group") {
    return {
      logicalOperator: node.logicalOperator ?? "AND",
      rules: (node.children ?? []).map((child) => serializeRuleNode(child))
    };
  }
  return {
    left: node.left,
    operator: node.operator,
    ...(node.rightMode === "indicator"
      ? { rightIndicator: node.rightIndicator || null }
      : { rightValue: toNumber(node.rightValue, 0) }),
    ...(node.rightValue2 ? { rightValue2: toNumber(node.rightValue2, 0) } : {})
  };
}

function serializeIndicator(indicator) {
  const normalized = normalizeIndicator(indicator);
  return {
    id: normalized.id,
    type: normalized.type,
    ...(normalized.subType ? { subType: normalized.subType } : {}),
    params: cloneValue(normalized.params),
    ...(normalized.input != null ? { input: serializeInput(normalized.input) } : {})
  };
}

function serializeInput(input) {
  if (typeof input === "string" || input == null) {
    return input;
  }
  return serializeIndicator(input);
}

export function buildDslPayload(builderState) {
  return {
    indicators: (builderState?.indicators ?? []).map((indicator) => serializeIndicator(indicator)),
    entryRules: serializeRuleNode(builderState?.entryRules ?? createRuleGroup()),
    exitRules: serializeRuleNode(builderState?.exitRules ?? createRuleGroup())
  };
}

export function mapPresetToDsl(strategyValue, params = {}) {
  const normalizedStrategy = String(strategyValue ?? "").trim().toUpperCase();
  if (normalizedStrategy === "BUY_AND_HOLD") {
    return {
      indicators: [],
      entryRules: createRuleGroup({
        children: [createCondition({ left: "close", operator: "GREATER_THAN", rightValue: 0 })]
      }),
      exitRules: createRuleGroup({
        children: [createCondition({ left: "close", operator: "LESS_THAN", rightValue: -1 })]
      })
    };
  }

  if (normalizedStrategy === "RSI") {
    const rsiIndicator = createIndicator("RSI", {
      id: "rsi",
      params: {
        period: toNumber(params.period, 14)
      }
    });
    return {
      indicators: [rsiIndicator],
      entryRules: createRuleGroup({
        children: [createCondition({
          left: "rsi",
          operator: "CROSS_ABOVE",
          rightValue: toNumber(params.oversold, 30)
        })]
      }),
      exitRules: createRuleGroup({
        children: [createCondition({
          left: "rsi",
          operator: "CROSS_BELOW",
          rightValue: toNumber(params.overbought, 70)
        })]
      })
    };
  }

  if (normalizedStrategy === "BOLLINGER_BANDS") {
    const bb = createIndicator("BOLLINGER", {
      id: "bb",
      params: {
        window: toNumber(params.window, 20),
        stdDevMultiplier: toNumber(params.stdDevMultiplier, 2)
      }
    });
    return {
      indicators: [bb],
      entryRules: createRuleGroup({
        children: [createCondition({
          left: "close",
          operator: "CROSS_ABOVE",
          rightMode: "indicator",
          rightIndicator: "bb.lower"
        })]
      }),
      exitRules: createRuleGroup({
        children: [createCondition({
          left: "close",
          operator: "CROSS_BELOW",
          rightMode: "indicator",
          rightIndicator: "bb.upper"
        })]
      })
    };
  }

  const fastMa = createIndicator("SMA", {
    id: "fastMa",
    params: {
      window: toNumber(params.shortWindow, 10)
    }
  });
  const slowMa = createIndicator("SMA", {
    id: "slowMa",
    params: {
      window: toNumber(params.longWindow, 50)
    }
  });

  return {
    indicators: [fastMa, slowMa],
    entryRules: createRuleGroup({
      children: [createCondition({
        left: "fastMa",
        operator: "CROSS_ABOVE",
        rightMode: "indicator",
        rightIndicator: "slowMa"
      })]
    }),
    exitRules: createRuleGroup({
      children: [createCondition({
        left: "fastMa",
        operator: "CROSS_BELOW",
        rightMode: "indicator",
        rightIndicator: "slowMa"
      })]
    })
  };
}

export function buildIndicatorReferenceOptions(indicators = []) {
  const options = [...BASE_INPUT_OPTIONS];
  indicators.forEach((indicator) => {
    const normalized = normalizeIndicator(indicator);
    getReferenceOutputs(normalized).forEach((output) => {
      if (!options.some((option) => option.value === output.value)) {
        options.push(output);
      }
    });
  });
  return options;
}

export function buildMissingIndicatorReferenceOptions(options, selectedValues = []) {
  const next = [...options];
  selectedValues.filter(Boolean).forEach((value) => {
    if (!next.some((option) => option.value === value)) {
      next.push({
        value,
        label: `${value} (missing)`
      });
    }
  });
  return next;
}

function inferStep(field, value) {
  if (field?.step != null) {
    return Number(field.step);
  }
  return Number.isInteger(Number(value)) ? 1 : 0.5;
}

export function collectDslOptimizationTargets(indicators = []) {
  return indicators.flatMap((indicator) => {
    const normalized = normalizeIndicator(indicator);
    const definition = getIndicatorDefinition(normalized.type);
    return getVisibleFields(definition, normalized)
      .filter((field) => field.type === "number" && field.optimizable !== false)
      .map((field) => ({
        key: `indicators.${normalized.id}.params.${field.name}`,
        label: `${normalized.id} ${field.label}`,
        defaultValue: toNumber(normalized.params[field.name], field.defaultValue ?? 0),
        min: field.min,
        max: field.max,
        step: inferStep(field, normalized.params[field.name])
      }));
  });
}

export function createOptimizationConfig(target) {
  const step = toNumber(target.step, 1);
  const start = toNumber(target.defaultValue, 0);
  const boundedEnd = target.max == null ? start + (step * 2) : Math.min(target.max, start + (step * 2));
  return {
    start,
    end: Math.max(start, boundedEnd),
    step
  };
}

export function buildRangeValues(config = {}) {
  const start = Number(config.start);
  const end = Number(config.end);
  const step = Number(config.step);
  if (!Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(step) || step <= 0 || start > end) {
    return [];
  }

  const values = [];
  const precision = `${step}`.includes(".") ? `${step}`.split(".")[1].length : 0;
  for (let value = start; value <= end + (step / 100); value += step) {
    values.push(Number(value.toFixed(Math.min(precision + 2, 6))));
  }
  return values;
}

export function buildDslParamGrid(optimizationConfig = {}) {
  return Object.fromEntries(
    Object.entries(optimizationConfig)
      .map(([key, config]) => [key, buildRangeValues(config)])
      .filter(([, values]) => values.length)
  );
}

export function countDslCombinations(optimizationConfig = {}) {
  const entries = Object.values(optimizationConfig).map((config) => buildRangeValues(config).length).filter(Boolean);
  if (!entries.length) {
    return 0;
  }
  return entries.reduce((total, count) => total * count, 1);
}

function validateIndicatorReference(reference, validReferences, messagePrefix, messages) {
  if (!reference) {
    messages.push(`${messagePrefix} is required.`);
    return;
  }
  if (!validReferences.has(reference)) {
    messages.push(`${messagePrefix} '${reference}' does not exist.`);
  }
}

function validateIndicatorDefinition(indicator, allReferences, messages, path = indicator.id) {
  const normalized = normalizeIndicator(indicator);
  const definition = getIndicatorDefinition(normalized.type);

  if (!normalized.id) {
    messages.push("Indicator id is required.");
  }

  if (definition.supportsInput) {
    if (typeof normalized.input === "string") {
      validateIndicatorReference(normalized.input, allReferences, `Input for ${path}`, messages);
      if (normalized.input === normalized.id || normalized.input.startsWith(`${normalized.id}.`)) {
        messages.push(`Indicator '${normalized.id}' cannot reference itself as input.`);
      }
    } else if (normalized.input && typeof normalized.input === "object") {
      validateIndicatorDefinition(normalized.input, allReferences, messages, `${path}.input`);
    }
  }

  definition.params.forEach((field) => {
    const value = normalized.params[field.name];
    if (field.type === "number") {
      if (!isFiniteNumber(value)) {
        messages.push(`${normalized.id} ${field.label} must be numeric.`);
        return;
      }
      if (field.min != null && Number(value) < field.min) {
        messages.push(`${normalized.id} ${field.label} must be at least ${field.min}.`);
      }
      if (field.max != null && Number(value) > field.max) {
        messages.push(`${normalized.id} ${field.label} must be no more than ${field.max}.`);
      }
    }
    if (field.type === "select") {
      const validOptions = new Set(field.options.map((option) => option.value));
      if (!validOptions.has(value)) {
        messages.push(`${normalized.id} ${field.label} has an unsupported value.`);
      }
    }
    if (field.type === "reference" && value) {
      validateIndicatorReference(value, allReferences, `${normalized.id} ${field.label}`, messages);
    }
  });
}

function validateRuleNode(node, validReferences, messages, scopeLabel) {
  if (!node) {
    messages.push(`${scopeLabel} rules are required.`);
    return;
  }
  if (node.kind === "group") {
    if (!["AND", "OR"].includes(node.logicalOperator)) {
      messages.push(`${scopeLabel} group operator must be AND or OR.`);
    }
    (node.children ?? []).forEach((child) => validateRuleNode(child, validReferences, messages, scopeLabel));
    return;
  }

  validateIndicatorReference(node.left, validReferences, `${scopeLabel} left reference`, messages);
  if (!RULE_OPERATOR_OPTIONS.some((option) => option.value === node.operator)) {
    messages.push(`${scopeLabel} operator is invalid.`);
  }
  if (node.rightMode === "indicator") {
    validateIndicatorReference(node.rightIndicator, validReferences, `${scopeLabel} right indicator`, messages);
  } else if (!isFiniteNumber(node.rightValue)) {
    messages.push(`${scopeLabel} threshold must be numeric.`);
  }
}

export function validateBuilderState(builderState) {
  const indicators = (builderState?.indicators ?? []).map((indicator) => normalizeIndicator(indicator));
  const messages = [];
  const ids = new Set();

  indicators.forEach((indicator) => {
    if (ids.has(indicator.id)) {
      messages.push(`Indicator id '${indicator.id}' must be unique.`);
    }
    ids.add(indicator.id);
  });

  const references = new Set(buildIndicatorReferenceOptions(indicators).map((option) => option.value));
  indicators.forEach((indicator) => validateIndicatorDefinition(indicator, references, messages));
  validateRuleNode(builderState?.entryRules ?? createRuleGroup(), references, messages, "Entry");
  validateRuleNode(builderState?.exitRules ?? createRuleGroup(), references, messages, "Exit");

  return [...new Set(messages)];
}

// ── Plain-English rule summary ─────────────────────────────────────────────

const OPERATOR_PROSE = {
  GREATER_THAN: "is above",
  LESS_THAN: "is below",
  EQUAL_TO: "equals",
  CROSS_ABOVE: "crosses above",
  CROSS_BELOW: "crosses below",
  IS_BETWEEN: "is between",
  INCREASED_BY_PCT: "has risen by",
  NBAR_HIGH: "is at",
  NBAR_LOW: "is at"
};

function resolveRefLabel(ref, references) {
  if (!ref) return "?";
  const found = (references ?? []).find((r) => r.value === ref);
  return found?.label ?? ref;
}

function formatRightSide(condition) {
  const { operator, rightValue, rightValue2 } = condition;
  if (operator === "IS_BETWEEN") return `${rightValue ?? 0} and ${rightValue2 ?? 0}`;
  if (operator === "INCREASED_BY_PCT") {
    const bars = rightValue2 ?? 1;
    return `${rightValue ?? 0}% over ${bars} bar${bars !== 1 ? "s" : ""}`;
  }
  if (operator === "NBAR_HIGH") return `${rightValue ?? 1}-bar high`;
  if (operator === "NBAR_LOW") return `${rightValue ?? 1}-bar low`;
  return String(rightValue ?? 0);
}

function summarizeNode(node, references) {
  if (!node) return "";
  if (node.kind === "group") {
    const parts = (node.children ?? []).map((c) => summarizeNode(c, references)).filter(Boolean);
    if (!parts.length) return "";
    const glue = node.logicalOperator === "OR" ? " OR " : " AND ";
    return parts.length > 1 ? `(${parts.join(glue)})` : parts[0];
  }
  const left = resolveRefLabel(node.left, references);
  const op = OPERATOR_PROSE[node.operator] ?? node.operator;
  const right = node.rightMode === "indicator"
    ? resolveRefLabel(node.rightIndicator, references)
    : formatRightSide(node);
  return `${left} ${op} ${right}`;
}

export function buildRuleSummary(ruleTree, references) {
  if (!ruleTree || ruleTree.kind !== "group") return null;
  const children = ruleTree.children ?? [];
  if (!children.length) return null;
  const parts = children.map((c) => summarizeNode(c, references)).filter(Boolean);
  if (!parts.length) return null;
  const glue = ruleTree.logicalOperator === "OR" ? " OR " : " AND ";
  return parts.join(glue);
}

// ── Condition templates ────────────────────────────────────────────────────

export const CONDITION_TEMPLATE_OPTIONS = [
  { value: "RSI_OVERSOLD_BOUNCE", label: "RSI Oversold Bounce", description: "Entry when RSI crosses above 30, exit above 70" },
  { value: "GOLDEN_CROSS", label: "Golden Cross", description: "Entry on SMA(50) crossing above SMA(200)" },
  { value: "MACD_SIGNAL", label: "MACD Signal Cross", description: "Entry when MACD crosses above its signal line" }
];

export function buildConditionTemplate(name) {
  if (name === "RSI_OVERSOLD_BOUNCE") {
    const rsi = createIndicator("RSI");
    return buildDslPayload({
      indicators: [rsi],
      entryRules: createRuleGroup({
        children: [createCondition({ left: rsi.id, operator: "CROSS_ABOVE", rightValue: 30 })]
      }),
      exitRules: createRuleGroup({
        children: [createCondition({ left: rsi.id, operator: "CROSS_ABOVE", rightValue: 70 })]
      })
    });
  }
  if (name === "GOLDEN_CROSS") {
    const fast = createIndicator("SMA", { id: "smaFast", params: { window: 50 }, style: { color: "#29d391" } });
    const slow = createIndicator("SMA", { id: "smaSlow", params: { window: 200 }, style: { color: "#f7c75f" } });
    return buildDslPayload({
      indicators: [fast, slow],
      entryRules: createRuleGroup({
        children: [createCondition({ left: fast.id, operator: "CROSS_ABOVE", rightMode: "indicator", rightIndicator: slow.id })]
      }),
      exitRules: createRuleGroup({
        children: [createCondition({ left: fast.id, operator: "CROSS_BELOW", rightMode: "indicator", rightIndicator: slow.id })]
      })
    });
  }
  if (name === "MACD_SIGNAL") {
    const macd = createIndicator("MACD");
    return buildDslPayload({
      indicators: [macd],
      entryRules: createRuleGroup({
        children: [createCondition({ left: macd.id, operator: "CROSS_ABOVE", rightMode: "indicator", rightIndicator: `${macd.id}.signal` })]
      }),
      exitRules: createRuleGroup({
        children: [createCondition({ left: macd.id, operator: "CROSS_BELOW", rightMode: "indicator", rightIndicator: `${macd.id}.signal` })]
      })
    });
  }
  return null;
}

export function hydrateBuilderStateFromDsl(payload = {}) {
  return {
    indicators: Array.isArray(payload.indicators)
      ? payload.indicators.map((indicator) => normalizeIndicator(indicator))
      : [],
    // Accept both the internal rule-tree format (entryRules/exitRules) and the
    // StrategyDSL string format (entry/exit). The string form is parsed by
    // parseDslExpression so users can paste StrategyDSL JSON directly.
    entryRules: payload.entryRules
      ? deserializeRuleNode(payload.entryRules)
      : parseDslExpression(payload.entry),
    exitRules: payload.exitRules
      ? deserializeRuleNode(payload.exitRules)
      : parseDslExpression(payload.exit)
  };
}

export function computeIndicatorSeries(indicators = [], candles = []) {
  if (!Array.isArray(candles) || !candles.length) {
    return {};
  }

  const cache = {
    open: candles.map((candle) => Number(candle.open)),
    high: candles.map((candle) => Number(candle.high)),
    low: candles.map((candle) => Number(candle.low)),
    close: candles.map((candle) => Number(candle.close)),
    volume: candles.map((candle) => Number(candle.volume))
  };
  const normalizedIndicators = indicators.map((indicator) => normalizeIndicator(indicator));
  const indicatorsById = Object.fromEntries(normalizedIndicators.map((indicator) => [indicator.id, indicator]));

  normalizedIndicators.forEach((indicator) => {
    buildSeries(cache, indicatorsById, candles, indicator);
  });

  return cache;
}

export function getIndicatorDisplayOptions() {
  return Object.values(INDICATOR_LIBRARY).map((indicator) => ({
    value: indicator.value,
    label: `${categoryLabel(indicator.category)} | ${indicator.shortLabel ?? indicator.label}`
  }));
}

export function getIndicatorVisibleFields(indicator) {
  return getVisibleFields(getIndicatorDefinition(indicator.type), indicator);
}
