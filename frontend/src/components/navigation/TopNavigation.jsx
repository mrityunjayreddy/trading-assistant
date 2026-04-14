import "./TopNavigation.css";

const MENU_ITEMS = [
  { key: "analysis",   label: "Trading Analysis", status: "Live UI" },
  { key: "strategies", label: "Strategies",        status: "Live UI" },
  { key: "ai",         label: "AI Studio",         status: "Live UI" },
  { label: "Orders", status: "Soon" },
  { label: "Risk",   status: "Soon" },
];

export default function TopNavigation({ activeDashboard, activeWorkspace, dashboardOptions, onDashboardChange, onWorkspaceChange }) {
  function handleDashboardSelection(nextDashboardValue) {
    onDashboardChange(nextDashboardValue);
    onWorkspaceChange("dashboard");
  }

  return (
    <header className="top-navigation">
      <div className="top-navigation__brand">
        <span className="top-navigation__eyebrow">Trading Console</span>
        <strong className="top-navigation__title">Crypto Assistant</strong>
      </div>

      <div className="top-navigation__menu">
        <nav className="top-navigation__links" aria-label="Primary menu">
          <div className="top-navigation__dropdown">
            <button
              className={`top-navigation__link ${activeWorkspace === "dashboard" ? "top-navigation__link--active" : ""} top-navigation__dropdown-trigger`.trim()}
              type="button"
              onClick={() => onWorkspaceChange("dashboard")}
            >
              <span>Dashboard</span>
              <span className="top-navigation__link-badge">{activeDashboard.label}</span>
              <span className="top-navigation__caret" aria-hidden="true">v</span>
            </button>

            <div className="top-navigation__dropdown-menu" role="menu" aria-label="Dashboard options">
              {dashboardOptions.map((option) => {
                const isActive = option.value === activeDashboard.value;
                return (
                  <button
                    key={option.value}
                    className={`top-navigation__dropdown-item ${isActive ? "top-navigation__dropdown-item--active" : ""}`.trim()}
                    type="button"
                    role="menuitem"
                    onClick={() => handleDashboardSelection(option.value)}
                  >
                    <span>{option.label}</span>
                    {isActive ? <span className="top-navigation__dropdown-check">Current</span> : null}
                  </button>
                );
              })}
            </div>
          </div>

          {MENU_ITEMS.map((item) => (
            <button
              key={item.label}
              className={`top-navigation__link ${activeWorkspace === item.key ? "top-navigation__link--active" : ""}`.trim()}
              type="button"
              onClick={() => item.key && onWorkspaceChange(item.key)}
            >
              <span>{item.label}</span>
              <span className="top-navigation__link-badge">{item.status}</span>
            </button>
          ))}
        </nav>
      </div>
    </header>
  );
}
