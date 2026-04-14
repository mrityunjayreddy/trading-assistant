# frontend

> React trading dashboard — displays live Binance Futures price data via SSE, provides an interactive strategy builder with Monaco editor, and visualises backtest results with an equity curve chart.

**Port:** `5173` (Vite dev server)  
**Stack:** React 18 | Vite 5 | TradingView Lightweight Charts | Monaco Editor

---

## Responsibilities

- Consumes Server-Sent Events from `trading-assistant-backend` to display live 1-minute candle updates in real time
- Renders OHLCV candlestick and equity curve charts using TradingView's Lightweight Charts library
- Provides a strategy DSL editor (Monaco) for authoring custom strategy JSON with syntax highlighting
- Allows users to submit backtests against any symbol and timeframe and view trade logs, equity curve, and key metrics
- Proxies all `/api` requests to `trading-assistant-backend :8080` — no direct service-to-service calls from the browser

---

## Project Structure

```
frontend/src/
├── App.jsx              — Root component + routing
├── main.jsx             — React entry point
├── components/          — Reusable UI components (charts, forms, tables)
├── hooks/               — Custom React hooks (useSSE, useBacktest, etc.)
├── services/            — API client functions (fetch wrappers)
├── state/               — Global state (React context or Zustand)
├── styles/              — CSS modules / global styles
└── utils/               — Helpers (formatters, date utils)
```

---

## Development Setup

### Prerequisites

| Tool | Version |
|---|---|
| Node.js | 20 LTS or newer |
| npm | 10+ (bundled with Node 20) |

### Install and run

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` in your browser.

**Backend must be running:** The dev server proxies `/api/*` to `http://localhost:8080`. Start `trading-assistant-backend` before opening the dashboard.

### Production build

```bash
npm run build
# Output: frontend/dist/
```

### Preview production build

```bash
npm run preview
# Serves the dist/ folder at http://localhost:4173
```

---

## Vite Proxy Configuration

The Vite dev server is configured to proxy two path prefixes:

| Path Prefix | Target | Notes |
|---|---|---|
| `/api` | `http://localhost:8080` | All trading-assistant-backend routes (SSE, candles, proxy to downstream services) |
| `/trading-engine-api` | `http://localhost:8082` | Direct trading-engine calls (path rewritten: `/trading-engine-api/foo` → `/foo`) |

This means the browser sees a single origin (`http://localhost:5173`) and never encounters CORS issues during development.

---

## Key Dependencies

| Package | Version | Purpose |
|---|---|---|
| `react` | 18.3 | UI framework |
| `lightweight-charts` | 5.1 | TradingView candlestick + line charts |
| `@monaco-editor/react` | 4.7 | VS Code-powered JSON strategy editor |
| `@dnd-kit/core` | 6.3 | Drag-and-drop strategy builder UI |
| `clsx` | 2.1 | Conditional CSS class utility |
| `vite` | 5.4 | Build tool and dev server |

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL for direct API calls (not used via proxy) |

Copy `.env.example` to `.env.local` to override:
```bash
cp .env.example .env.local
```

---

## Known Limitations / Future Improvements

- **No authentication** — all API calls are unauthenticated; adding JWT token management in the service layer is a future task
- **No test suite** — unit tests for hooks and components should be added using Vitest + React Testing Library
- **Single timeframe view** — the dashboard currently shows 1m candles; adding a timeframe selector would improve usability
- **Mobile layout** — the UI is desktop-first; responsive breakpoints need work for smaller screens
