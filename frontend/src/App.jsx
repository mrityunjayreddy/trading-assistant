import { useState } from "react";
import TradingAnalysisPage from "./components/analysis/TradingAnalysisPage";
import DashboardPage from "./components/dashboard/DashboardPage";
import TopNavigation from "./components/navigation/TopNavigation";
import StrategyLibrary from "./components/strategies/StrategyLibrary";
import AiPanel from "./components/ai/AiPanel";
import { useMarketData } from "./hooks/useMarketData";
import { useMarketStats } from "./hooks/useMarketStats";
import { useTradingAnalysisData } from "./hooks/useTradingAnalysisData";

const DASHBOARD_OPTIONS = [
  { value: "usm-futures", label: "USM Futures Dashboard" },
  { value: "spot", label: "Spot Dashboard (Soon)" },
  { value: "options", label: "Options Dashboard (Soon)" }
];

export default function App() {
  const marketData = useMarketData();
  const stats = useMarketStats(marketData.candles);
  const [activeDashboardValue, setActiveDashboardValue] = useState(DASHBOARD_OPTIONS[0].value);
  const [activeWorkspace, setActiveWorkspace] = useState("dashboard");
  const activeDashboard = DASHBOARD_OPTIONS.find((option) => option.value === activeDashboardValue) ?? DASHBOARD_OPTIONS[0];
  const analysisData = useTradingAnalysisData(marketData.symbols);

  const isAnalysis = activeWorkspace === "analysis";

  return (
    <main className={isAnalysis ? "app-shell--analysis" : "app-shell"}>
      <TopNavigation
        activeDashboard={activeDashboard}
        activeWorkspace={activeWorkspace}
        dashboardOptions={DASHBOARD_OPTIONS}
        onDashboardChange={setActiveDashboardValue}
        onWorkspaceChange={setActiveWorkspace}
      />
      {isAnalysis && (
        <TradingAnalysisPage
          analysisData={analysisData}
          symbols={marketData.symbols}
          onBack={() => setActiveWorkspace("dashboard")}
        />
      )}
      {activeWorkspace === "strategies" && <StrategyLibrary />}
      {activeWorkspace === "ai" && <AiPanel />}
      {activeWorkspace !== "analysis" && activeWorkspace !== "strategies" && activeWorkspace !== "ai" && (
        <DashboardPage
          marketData={marketData}
          stats={stats}
        />
      )}
    </main>
  );
}
