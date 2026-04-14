import FeedPill from "../shared/FeedPill";
import Panel from "../shared/Panel";
import PanelHeader from "../shared/PanelHeader";
import { formatPercent, formatPrice } from "../../utils/formatters";
import PriceChart from "./PriceChart";
import "./PriceStructurePanel.css";

export default function PriceStructurePanel({ candles, connectionState }) {
  const latestCandle = candles[candles.length - 1];
  const previousCandle = candles[candles.length - 2];
  const priceMove = latestCandle && previousCandle
    ? ((latestCandle.close - previousCandle.close) / previousCandle.close) * 100
    : 0;
  const moveTone = priceMove > 0 ? "positive" : priceMove < 0 ? "negative" : "";

  return (
    <Panel as="article">
      <PanelHeader
        title="Price Structure"
        subtitle="Close trajectory with relative volume bars underneath each candle."
        action={(
          <div className="price-structure-meta">
            {latestCandle ? (
              <div className="price-structure-value">
                <span className="price-structure-label">Current Price</span>
                <span className={`price-structure-price ${moveTone}`.trim()}>
                  {formatPrice(latestCandle.close)}
                </span>
                <span className={`price-structure-move ${moveTone}`.trim()}>
                  {formatPercent(priceMove)}
                </span>
              </div>
            ) : null}
            <FeedPill connectionState={connectionState} />
          </div>
        )}
      />
      <PriceChart candles={candles} />
    </Panel>
  );
}
