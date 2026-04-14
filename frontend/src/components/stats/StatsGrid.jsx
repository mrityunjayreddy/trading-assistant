import StatsCard from "./StatsCard";
import { formatCompact, formatPercent, formatPrice } from "../../utils/formatters";
import "./StatsGrid.css";

export default function StatsGrid({ stats }) {
  const trendTone = stats.percentMove > 0 ? "positive" : stats.percentMove < 0 ? "negative" : "";

  const statItems = [
    {
      footnote: "Most recent close from the live one-minute candle.",
      title: "Last Price",
      value: formatPrice(stats.lastPrice)
    },
    {
      footnote: "Visible high-low range across the loaded window.",
      title: "Range",
      value: `${formatPrice(stats.rangeLow)} - ${formatPrice(stats.rangeHigh)}`
    },
    {
      footnote: "Change from first open to latest close in the current buffer.",
      title: "Window Move",
      tone: trendTone,
      value: formatPercent(stats.percentMove)
    },
    {
      footnote: "Summed contract volume across the buffered candles.",
      title: "Volume",
      value: formatCompact(stats.volume)
    }
  ];

  return (
    <section className="stats-grid">
      {statItems.map((item) => (
        <StatsCard
          key={item.title}
          footnote={item.footnote}
          title={item.title}
          tone={item.tone}
          value={item.value}
        />
      ))}
    </section>
  );
}
