import { formatTime } from "../../utils/formatters";
import "./HeroMeta.css";

export default function HeroMeta({ candlesCount, lastMessageAt, selectedSymbol }) {
  const lastUpdateLabel = lastMessageAt ? formatTime(lastMessageAt) : "Awaiting ticks";

  return (
    <div className="hero-meta">
      <div className="meta-pill">Active market: {selectedSymbol}</div>
      <div className="meta-pill">Candles loaded: {candlesCount}</div>
      <div className="meta-pill">Last stream event: {lastUpdateLabel}</div>
    </div>
  );
}
