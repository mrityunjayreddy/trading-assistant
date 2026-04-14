export class MarketCatalogService {
  async fetchSymbols() {
    const response = await fetch("/api/markets");
    if (!response.ok) {
      throw new Error(`Unable to load markets (${response.status})`);
    }

    const payload = await response.json();
    return Array.isArray(payload.symbols) ? payload.symbols : [];
  }
}
