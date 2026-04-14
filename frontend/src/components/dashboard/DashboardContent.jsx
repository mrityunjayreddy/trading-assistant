import CandlesPanel from "../market/CandlesPanel";
import PriceStructurePanel from "../market/PriceStructurePanel";

export default function DashboardContent({ candles, connectionState }) {
  return (
    <section className="content-stack">
      <PriceStructurePanel candles={candles} connectionState={connectionState} />
      <CandlesPanel candles={candles} />
    </section>
  );
}
