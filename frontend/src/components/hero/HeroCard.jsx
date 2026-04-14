import HeroMeta from "./HeroMeta";
import "./HeroCard.css";

export default function HeroCard({ candlesCount, lastMessageAt, selectedSymbol }) {
  return (
    <div className="hero-card">
      <div className="eyebrow">USD-M Futures Dashboard</div>
      <h1>Trade the tape with live Binance futures flow.</h1>
      <p className="hero-copy">
        This console reads your Spring market-data service directly, keeps the active symbol synced over
        server-sent events, and highlights the latest 1-minute structure for fast discretionary review.
      </p>
      <HeroMeta
        candlesCount={candlesCount}
        lastMessageAt={lastMessageAt}
        selectedSymbol={selectedSymbol}
      />
    </div>
  );
}
