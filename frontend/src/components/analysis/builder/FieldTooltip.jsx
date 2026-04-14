export default function FieldTooltip({ text }) {
  return (
    <span className="builder-tooltip" title={text} aria-label={text}>
      ?
    </span>
  );
}
