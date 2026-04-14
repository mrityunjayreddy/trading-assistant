export class TradingSimulationService {
  async fetchStrategies() {
    const response = await fetch("/trading-engine-api/api/v1/strategies");

    if (!response.ok) {
      throw new Error(`Unable to load strategies (${response.status})`);
    }

    const payload = await response.json();
    return Array.isArray(payload) ? payload : [];
  }

  async fetchExecutionModels() {
    const response = await fetch("/trading-engine-api/api/v1/execution-models");

    if (!response.ok) {
      throw new Error(`Unable to load execution models (${response.status})`);
    }

    const payload = await response.json();
    return Array.isArray(payload) ? payload : [];
  }

  async simulate({ symbol, interval, strategy, params, execution, assumptions, range, indicators, entryRules, exitRules }) {
    return this.postJson("/trading-engine-api/api/v1/simulate", {
      symbol,
      interval,
      strategy,
      params,
      indicators,
      entryRules,
      exitRules,
      execution,
      assumptions,
      range
    }, "Unable to run simulation");
  }

  async optimize({ symbol, interval, strategy, paramGrid, metric, execution, assumptions, range, indicators, entryRules, exitRules }) {
    return this.postJson("/trading-engine-api/api/v1/optimize", {
      symbol,
      interval,
      strategy,
      paramGrid,
      metric,
      indicators,
      entryRules,
      exitRules,
      execution,
      assumptions,
      range
    }, "Unable to run optimization");
  }

  async postJson(url, payload, fallbackMessage) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      let message = `${fallbackMessage} (${response.status})`;

      try {
        const payload = await response.json();
        if (payload?.message) {
          message = payload.message;
        }
      } catch {
        // Ignore JSON parsing errors and keep the fallback message.
      }

      throw new Error(message);
    }

    return response.json();
  }
}
