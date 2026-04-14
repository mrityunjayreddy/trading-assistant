import CandlesTable from "./CandlesTable";
import Panel from "../shared/Panel";
import PanelHeader from "../shared/PanelHeader";
import { formatCompact, formatPrice, formatTime } from "../../utils/formatters";
import "./CandlesPanel.css";

export default function CandlesPanel({ candles }) {
  const latestCandle = candles[candles.length - 1];

  return (
    <Panel as="aside" className="candles-panel">
      <PanelHeader
        title="Recent Candles"
        subtitle="A more readable view of the latest one-minute candles and the active tape."
      />
      {latestCandle ? (
        <div className="candles-summary">
          <div className="summary-chip">
            <span className="summary-chip-label">Latest close</span>
            <strong>{formatPrice(latestCandle.close)}</strong>
          </div>
          <div className="summary-chip">
            <span className="summary-chip-label">Latest volume</span>
            <strong>{formatCompact(latestCandle.volume)}</strong>
          </div>
          <div className="summary-chip">
            <span className="summary-chip-label">Updated</span>
            <strong>{formatTime(latestCandle.closeTime)}</strong>
          </div>
        </div>
      ) : null}
      <CandlesTable candles={candles} />
    </Panel>
  );
}
