import FeedPill from "../shared/FeedPill";
import Panel from "../shared/Panel";
import "./StatusPanel.css";

export default function StatusPanel({ connectionState, errorMessage, selectedSymbol }) {
  return (
    <Panel className="status-panel">
      <p className="panel-label">Feed Status</p>
      <div className="status-grid">
        <div>
          <FeedPill connectionState={connectionState} />
          <p className="muted">
            {errorMessage || "REST bootstrap and SSE stream are running through the Spring Boot backend."}
          </p>
        </div>
        <div>
          <div className="status-value">{selectedSymbol}</div>
          <p className="muted">Symbol currently bound to the dashboard session.</p>
        </div>
      </div>
    </Panel>
  );
}
