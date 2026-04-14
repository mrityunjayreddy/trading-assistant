import "./Panel.css";

export default function Panel({ as: Component = "section", children, className = "" }) {
  const mergedClassName = ["panel", className].filter(Boolean).join(" ");

  return <Component className={mergedClassName}>{children}</Component>;
}
