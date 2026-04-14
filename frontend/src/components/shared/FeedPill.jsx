import "./FeedPill.css";

const FEED_VARIANTS = {
  connecting: {
    dotClassName: "feed-dot",
    label: "Syncing feed"
  },
  error: {
    dotClassName: "feed-dot error",
    label: "Feed disconnected"
  },
  live: {
    dotClassName: "feed-dot live",
    label: "Live market feed"
  }
};

export default function FeedPill({ connectionState }) {
  const variant = FEED_VARIANTS[connectionState] ?? FEED_VARIANTS.connecting;

  return (
    <div className="feed-status">
      <span className={variant.dotClassName} />
      <span>{variant.label}</span>
    </div>
  );
}
