import Panel from "../shared/Panel";
import "./MarketSelectorPanel.css";

export default function MarketSelectorPanel({ onSymbolChange, selectedSymbol, symbols }) {
  return (
    <Panel className="control-panel">
      <p className="panel-label">Market Selector</p>
      <select
        className="symbol-select"
        value={selectedSymbol}
        onChange={(event) => onSymbolChange(event.target.value)}
      >
        {symbols.map((symbol) => (
          <option key={symbol} value={symbol}>
            {symbol}
          </option>
        ))}
      </select>
    </Panel>
  );
}
