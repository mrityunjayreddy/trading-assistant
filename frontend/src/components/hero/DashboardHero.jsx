import HeroCard from "./HeroCard";
import MarketSelectorPanel from "./MarketSelectorPanel";
import StatusPanel from "./StatusPanel";
import "./DashboardHero.css";

export default function DashboardHero({ marketData }) {
  const {
    candles,
    connectionState,
    errorMessage,
    lastMessageAt,
    selectedSymbol,
    setSelectedSymbol,
    symbols
  } = marketData;

  return (
    <section className="hero">
      <HeroCard
        candlesCount={candles.length}
        lastMessageAt={lastMessageAt}
        selectedSymbol={selectedSymbol}
      />

      <div className="hero-side">
        <MarketSelectorPanel
          selectedSymbol={selectedSymbol}
          symbols={symbols}
          onSymbolChange={setSelectedSymbol}
        />
        <StatusPanel
          connectionState={connectionState}
          errorMessage={errorMessage}
          selectedSymbol={selectedSymbol}
        />
      </div>
    </section>
  );
}
