import DashboardContent from "./DashboardContent";
import DashboardHero from "../hero/DashboardHero";
import StatsGrid from "../stats/StatsGrid";
import StrategyLeaderboard from "../StrategyLeaderboard";
import "./DashboardPage.css";

export default function DashboardPage({ marketData, stats }) {
  return (
    <>
      <DashboardHero marketData={marketData} />
      <StatsGrid stats={stats} />
      <DashboardContent
        candles={marketData.candles}
        connectionState={marketData.connectionState}
      />
      <StrategyLeaderboard />
    </>
  );
}
