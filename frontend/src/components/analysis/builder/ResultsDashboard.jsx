import Panel from "../../shared/Panel";
import { formatPercent, formatPrice } from "../../../utils/formatters";
import StrategyMarketChart from "./StrategyMarketChart";

function formatMetricLabel(metric) {
  return metric?.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (character) => character.toUpperCase()) ?? "Metric";
}

export default function ResultsDashboard({
  analysisMode,
  dslState,
  executionProfile,
  interval,
  optimizationResult,
  result,
  summary,
  symbol,
  templateLabel,
  tradeDirectionLabel
}) {
  return (
    <div className="builder-results-shell">
      <div className="builder-summary-grid">
        <Panel className="builder-summary-card">
          <span className="builder-section__eyebrow">Strategy</span>
          <h3>{templateLabel}</h3>
          <p>{executionProfile} · {tradeDirectionLabel}</p>
        </Panel>
        <Panel className="builder-summary-card">
          <span className="builder-section__eyebrow">{analysisMode === "OPTIMIZATION" ? "Best Score" : "Total Return"}</span>
          <h3>{analysisMode === "OPTIMIZATION" ? summary.bestScore?.toFixed?.(2) ?? "0.00" : formatPercent(summary.totalReturn)}</h3>
          <p>{analysisMode === "OPTIMIZATION" ? formatMetricLabel(summary.metricUsed) : "Backtest result"}</p>
        </Panel>
        <Panel className="builder-summary-card">
          <span className="builder-section__eyebrow">{analysisMode === "OPTIMIZATION" ? "Successful Runs" : "Final Balance"}</span>
          <h3>{analysisMode === "OPTIMIZATION" ? summary.successfulCombinations : formatPrice(summary.finalBalance)}</h3>
          <p>{analysisMode === "OPTIMIZATION" ? `${summary.evaluatedCombinations} evaluated` : `${summary.tradesCount} executions`}</p>
        </Panel>
      </div>

      {analysisMode === "OPTIMIZATION" ? (
        <Panel className="builder-section">
          <div className="builder-section__header">
            <div>
              <span className="builder-section__eyebrow">5. Optimization Results</span>
              <h3>Parameter leaderboard</h3>
              <p>Review the top combinations generated from the DSL optimization ranges.</p>
            </div>
          </div>

          {optimizationResult ? (
            <div className="builder-table-shell">
              <table className="builder-table">
                <thead>
                  <tr>
                    <th>Rank</th>
                    <th>Parameters</th>
                    <th>Score</th>
                    <th>Total Return</th>
                    <th>Max Drawdown</th>
                    <th>Sharpe</th>
                    <th>Win Rate</th>
                    <th>Trades</th>
                  </tr>
                </thead>
                <tbody>
                  {optimizationResult.topResults.map((item, index) => (
                    <tr key={`${item.score}-${index + 1}`}>
                      <td>{index + 1}</td>
                      <td>{Object.entries(item.params).map(([key, value]) => `${key}: ${value}`).join(" | ")}</td>
                      <td>{item.score.toFixed(2)}</td>
                      <td>{formatPercent(item.totalReturn)}</td>
                      <td>{formatPercent(item.maxDrawdown)}</td>
                      <td>{item.sharpeRatio.toFixed(2)}</td>
                      <td>{formatPercent(item.winRate)}</td>
                      <td>{item.tradesCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="builder-empty">Run an optimization to populate the leaderboard.</div>
          )}
        </Panel>
      ) : (
        <Panel className="builder-section">
          <div className="builder-section__header">
            <div>
              <span className="builder-section__eyebrow">5. Results Dashboard</span>
              <h3>Backtest outcomes</h3>
              <p>Inspect market context, equity curve, and the executed trade ledger.</p>
            </div>
          </div>

          {result ? (
            <>
              <StrategyMarketChart dslState={dslState} interval={interval} result={result} symbol={symbol} />
              <div className="builder-table-shell">
                <table className="builder-table">
                  <thead>
                    <tr>
                      <th>Execution</th>
                      <th>Side</th>
                      <th>Price</th>
                      <th>Quantity</th>
                      <th>Notional</th>
                      <th>PnL</th>
                      <th>Cash After</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.trades.map((trade) => (
                      <tr key={`${trade.timestamp}-${trade.executionType}`}>
                        <td>{trade.executionType}</td>
                        <td>{trade.positionSide}</td>
                        <td>{formatPrice(trade.price)}</td>
                        <td>{trade.quantity.toFixed(4)}</td>
                        <td>{formatPrice(trade.notional)}</td>
                        <td>{formatPrice(trade.realizedPnl ?? 0)}</td>
                        <td>{formatPrice(trade.cashBalanceAfter)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : (
            <div className="builder-empty">Run a backtest to populate the dashboard.</div>
          )}
        </Panel>
      )}
    </div>
  );
}
