import EmptyState from "../shared/EmptyState";
import { formatCompact, formatPrice, formatTime } from "../../utils/formatters";
import "./CandlesTable.css";

export default function CandlesTable({ candles }) {
  if (!candles.length) {
    return <EmptyState message="No candles available yet for this symbol." />;
  }

  return (
    <div className="table-wrap candles-table-wrap">
      <table className="table candles-table">
        <thead>
          <tr>
            <th>Time</th>
            <th>Open</th>
            <th>High</th>
            <th>Low</th>
            <th>Close</th>
            <th>Volume</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {[...candles].reverse().map((candle, index, reversed) => {
            const previous = reversed[index + 1];
            const move = previous ? candle.close - previous.close : 0;
            const priceTone = move > 0 ? "positive" : move < 0 ? "negative" : "";
            const statusClassName = candle.closed ? "candle-status" : "candle-status building";

            return (
              <tr key={candle.openTime}>
                <td className="time-cell">
                  <span className="time-primary">{formatTime(candle.openTime)}</span>
                  <span className="time-secondary">{candle.closed ? "Settled candle" : "Live forming candle"}</span>
                </td>
                <td>{formatPrice(candle.open)}</td>
                <td>{formatPrice(candle.high)}</td>
                <td>{formatPrice(candle.low)}</td>
                <td className={`price-cell ${priceTone}`.trim()}>{formatPrice(candle.close)}</td>
                <td>{formatCompact(candle.volume)}</td>
                <td>
                  <span className={statusClassName}>{candle.closed ? "Closed" : "Building"}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
