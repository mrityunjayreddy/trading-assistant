const currencyFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 2
});

const compactNumberFormatter = new Intl.NumberFormat("en-US", {
  notation: "compact",
  maximumFractionDigits: 2
});

export function formatPrice(value) {
  return currencyFormatter.format(Number.isFinite(value) ? value : 0);
}

export function formatCompact(value) {
  return compactNumberFormatter.format(Number.isFinite(value) ? value : 0);
}

export function formatPercent(value) {
  if (!Number.isFinite(value)) {
    return "0.00%";
  }

  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

export function formatTime(timestamp) {
  return new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "numeric"
  }).format(new Date(timestamp));
}
