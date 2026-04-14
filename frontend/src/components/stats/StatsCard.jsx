import "./StatsCard.css";

export default function StatsCard({ footnote, title, tone, value }) {
  const className = tone ? `stat-value ${tone}` : "stat-value";

  return (
    <article className="stat-card">
      <p className="stat-title">{title}</p>
      <p className={className}>{value}</p>
      <div className="stat-footnote">{footnote}</div>
    </article>
  );
}
