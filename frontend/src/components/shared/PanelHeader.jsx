import "./PanelHeader.css";

export default function PanelHeader({ action = null, subtitle, title }) {
  return (
    <div className="panel-header">
      <div>
        <h2 className="panel-title">{title}</h2>
        <p className="panel-subtitle">{subtitle}</p>
      </div>
      {action}
    </div>
  );
}
