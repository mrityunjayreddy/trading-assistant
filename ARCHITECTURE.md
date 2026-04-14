# Architecture

This document describes the system architecture of the Crypto Trading Assistant at multiple levels of detail, following the C4 model convention.

---

## C4 Level 1 — System Context

```mermaid
graph TB
    Trader["Trader / Analyst<br/>(Browser)"]
    System["Crypto Trading Assistant<br/>(This System)"]
    Binance["Binance Futures<br/>(External Exchange API)"]
    Anthropic["Anthropic Claude API<br/>(LLM Provider)"]

    Trader -->|"Views live prices,<br/>runs backtests,<br/>reviews AI strategies"| System
    System -->|"Streams klines + trades<br/>via WebSocket"| Binance
    System -->|"Fetches historical<br/>klines via REST"| Binance
    System -->|"Generates strategy DSL<br/>via Claude API"| Anthropic
```

The system has one user (the trader/analyst), two external dependencies (Binance and Anthropic), and no real-money order execution — it is a read-only/analytics platform.

---

## C4 Level 2 — Container Diagram

```mermaid
graph TB
    subgraph Browser
        FE["React Frontend<br/>Vite SPA<br/>:5173"]
    end

    subgraph Backend Services
        TAB["trading-assistant-backend<br/>Spring Boot MVC + WebFlux<br/>:8080"]
        MDS["market-data-service<br/>Spring WebFlux<br/>:8081"]
        TE["trading-engine<br/>Spring WebFlux + ta4j<br/>:8082"]
        SS["strategy-service<br/>Spring Boot MVC<br/>:8083"]
        AI["ai-service<br/>Spring Boot MVC<br/>:8084"]
    end

    subgraph Data Stores
        KAFKA["Apache Kafka<br/>Topics: market.trades,<br/>market.kline.*"]
        PG["PostgreSQL 16<br/>Tables: market_data,<br/>strategies, backtest_results,<br/>strategy_memory"]
        REDIS["Redis 7<br/>LRU Cache"]
    end

    subgraph External
        BWS["Binance WebSocket<br/>wss://fstream.binance.com"]
        BREST["Binance REST<br/>https://fapi.binance.com"]
        CLAUDE["Anthropic Claude<br/>https://api.anthropic.com"]
    end

    FE -->|"REST + SSE"| TAB
    TAB -->|"Kafka publish"| KAFKA
    TAB -->|"JDBC write"| PG
    TAB -->|"WebSocket"| BWS
    TAB -->|"HTTP proxy"| SS
    TAB -->|"HTTP proxy"| AI

    MDS -->|"WebSocket"| BWS
    MDS -->|"Kafka publish"| KAFKA
    KAFKA -->|"Batch consume"| MDS
    MDS -->|"JDBC write"| PG

    TE -->|"REST"| BREST
    TE -->|"R2DBC write"| PG

    SS -->|"REST"| TE
    SS -->|"JPA"| PG
    SS -->|"Kafka"| KAFKA

    AI -->|"REST"| TE
    AI -->|"REST"| SS
    AI -->|"JPA + pgvector"| PG
    AI -->|"HTTP"| CLAUDE
```

---

## C4 Level 3 — Key Component Detail

### trading-engine internals

```mermaid
graph LR
    subgraph Controllers
        SC["SimulationController<br/>POST /api/v1/simulate"]
        DSC["DslSimulationController<br/>POST /api/v1/simulate/dsl<br/>POST /api/v1/backtest/validate"]
        OC["OptimizationController<br/>POST /api/v1/optimize"]
    end

    subgraph Services
        TSS["TradingSimulationService"]
        BS["BacktestingService<br/>Portfolio state machine"]
        HDS["HistoricalDataService<br/>Binance REST client"]
        OS["OptimizationService<br/>Grid search + virtual threads"]
    end

    subgraph TA4J Layer
        SDR["StrategyDefinitionResolver"]
        TSB["Ta4jStrategyBuilder"]
        MS["MetricService"]
    end

    subgraph Strategies
        MAS["MovingAverageStrategy"]
        RSIS["RsiStrategy"]
        BBS["BollingerBandsStrategy"]
        DSL["DSL-defined strategies"]
    end

    subgraph Output Pipeline
        SVE["StatisticalValidityEnricher"]
        BRP["BacktestResultPersister<br/>(R2DBC)"]
    end

    SC --> TSS
    DSC --> TSS
    OC --> OS
    OS --> TSS
    TSS --> HDS
    TSS --> SDR
    SDR --> TSB
    TSS --> BS
    BS --> MS
    TSB --> MAS
    TSB --> RSIS
    TSB --> BBS
    TSB --> DSL
    BS --> SVE
    SVE --> BRP
```

### ai-service internals

```mermaid
graph TB
    API["AiController<br/>(REST API)"]
    LLO["LearningLoopOrchestrator<br/>@Scheduled cron 02:00 UTC"]
    SGS["StrategyGenerationService<br/>Claude prompt builder"]
    MCA["MarketContextAnalyzer<br/>Feature extractor"]
    SMR["StrategyMemoryReader<br/>pgvector similarity query"]
    SMW["StrategyMemoryWriter<br/>Embedding + upsert"]
    AC["AnthropicClient<br/>OkHttp REST client"]
    LHM["LoopHealthMonitor<br/>Audit log"]

    API --> LLO
    API --> SGS
    API --> SMR
    API --> LHM

    LLO --> SMW
    LLO --> SGS
    LLO --> MCA

    SGS --> MCA
    SGS --> SMR
    SGS --> AC

    SMW --> PG["PostgreSQL<br/>strategy_memory table"]
    SMR --> PG
```

---

## End-to-End Data Flows

### Flow 1: Live Candle Streaming to Browser

```mermaid
sequenceDiagram
    participant B as Binance WS
    participant TAB as trading-assistant-backend
    participant K as Kafka
    participant MDS as market-data-service
    participant PG as PostgreSQL
    participant FE as Frontend (SSE)

    B-->>TAB: kline frame (1m, BTC)
    TAB->>TAB: parse JSON → Candle record
    TAB->>FE: SSE event (live candle)
    TAB->>K: produce → market.kline.btcusdt
    TAB->>PG: JDBC upsert market_data (closed candles only)

    Note over MDS,K: Parallel path via market-data-service
    B-->>MDS: kline frame (1m/5m/15m/1h, BTC/ETH/SOL)
    MDS->>K: produce → market.kline.btcusdt_1m (etc.)
    K-->>MDS: batch consume (up to 50 records / 10s)
    MDS->>PG: JDBC batch upsert market_data
```

### Flow 2: Backtest Simulation Request

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant TAB as trading-assistant-backend
    participant TE as trading-engine
    participant B as Binance REST
    participant PG as PostgreSQL

    FE->>TAB: POST /api/sim/api/v1/simulate
    TAB->>TE: proxy POST /api/v1/simulate
    TE->>B: GET /fapi/v1/klines?symbol=BTCUSDT&interval=1d
    B-->>TE: 1000 OHLCV candles (JSON array)
    TE->>TE: build ta4j BarSeries
    TE->>TE: run strategy → generate signals
    TE->>TE: simulate portfolio (fee-aware PnL)
    TE->>TE: compute metrics (Sharpe, drawdown, win rate)
    TE->>TE: enrich statistical validity
    TE->>PG: R2DBC INSERT backtest_results
    TE-->>TAB: SimulationResult (JSON)
    TAB-->>FE: SimulationResult
```

### Flow 3: AI Learning Loop (Nightly)

```mermaid
sequenceDiagram
    participant CRON as Scheduler (02:00 UTC)
    participant AI as ai-service
    participant PG as PostgreSQL/pgvector
    participant CLAUDE as Anthropic Claude
    participant TE as trading-engine
    participant SS as strategy-service

    CRON->>AI: triggerLoop("scheduled")
    AI->>PG: SELECT * FROM backtest_results ORDER BY created_at DESC
    AI->>AI: embed market context (symbol, regime, volatility, session)
    AI->>PG: UPDATE strategy_memory (store new memories)
    AI->>PG: cosine similarity search → top-5 memories
    AI->>CLAUDE: POST /v1/messages (system + user prompt with memories)
    CLAUDE-->>AI: JSON StrategyDSL
    AI->>AI: validate DSL schema (name, indicators, entry, exit, risk)
    AI->>TE: POST /api/v1/simulate/dsl (90-day backtest)
    TE-->>AI: SimulationResult + Sharpe ratio
    alt Sharpe > 0.3
        AI->>SS: POST /api/strategies (register DSL)
        SS->>SS: enforce LLM source limit (max 50, evict oldest)
    end
    AI->>AI: prune low-performing memories
    AI->>AI: update LoopRunSummary + health metrics
```

---

## Database Schema

```sql
-- OHLCV candle storage — idempotent upsert via 4-column unique key
market_data          (exchange, symbol, interval, open_time UNIQUE)

-- Strategy definitions stored as JSONB — supports GIN index queries
strategies           (id UUID, name, source CHECK IN ('BUILTIN','USER','LLM','EVOLVED'), dsl JSONB, is_active)

-- Simulation results — linked to strategies
backtest_results     (id UUID, strategy_id FK, symbol, interval, from_time, to_time,
                      total_trades, win_rate, total_pnl, max_drawdown, sharpe_ratio,
                      is_statistically_valid, validation_note)

-- Vector memory store for RAG — 1536-dimension cosine similarity
strategy_memory      (id UUID, strategy_id FK, symbol, interval, embedding vector(1536),
                      document TEXT, sharpe_ratio, verdict, created_at)
```

**Index strategy:**
- `market_data`: composite B-tree `(exchange, symbol, interval, open_time DESC)` for time-range queries
- `strategies`: GIN index on `dsl` JSONB column for field-level filtering; partial B-tree on `(is_active = true)` for listing
- `strategy_memory`: IVFFlat ANN index on `embedding` (created after initial data load; falls back to sequential scan until then)

---

## Security Architecture

| Concern | Current Approach | Production Recommendation |
|---|---|---|
| **Authentication** | None (local dev) | JWT tokens issued by an auth service; verify in BFF gateway |
| **API Key Management** | Environment variables (`.env` file) | Kubernetes Secrets or HashiCorp Vault |
| **Binance Keys** | Not required for public data streams | Store in Vault; inject as env vars at runtime |
| **Anthropic API Key** | `ANTHROPIC_API_KEY` env var | Vault secret; rotate regularly |
| **Database Credentials** | `POSTGRES_USER/PASSWORD` env vars | Vault dynamic secrets; short-lived credentials |
| **CORS** | Allowed origin `http://localhost:5173` | Restrict to production domain in `WebConfig` |
| **Network** | All services on localhost | Service mesh (Istio mTLS) between pods in Kubernetes |

---

## Observability

| Signal | Implementation | Notes |
|---|---|---|
| **Structured Logs** | SLF4J + Logback | Each log line includes service name, class, correlation context |
| **Log Levels** | DEBUG for business logic, INFO for lifecycle events, WARN for reconnects/retries, ERROR for failures | Configured per-package in `application.properties` |
| **Key Log Events** | Simulation start/complete, Kafka publish/consume batch size, WebSocket connect/disconnect, learning loop step completion | Enables log-based alerting in production |
| **Metrics (Planned)** | Micrometer + Prometheus | `/actuator/prometheus` on all services |
| **Tracing (Planned)** | OpenTelemetry + Jaeger | Trace IDs across service calls |
| **Health Checks** | Spring Boot Actuator `/actuator/health` | Ready for Kubernetes liveness/readiness probes |

---

## Deployment Architecture (Target)

```mermaid
graph TB
    subgraph k8s["Kubernetes Cluster"]
        subgraph ns_trading["Namespace: trading"]
            TAB_pod["trading-assistant-backend<br/>Deployment (2 replicas)"]
            MDS_pod["market-data-service<br/>Deployment (1 replica)"]
            TE_pod["trading-engine<br/>Deployment (3 replicas)"]
            SS_pod["strategy-service<br/>Deployment (2 replicas)"]
            AI_pod["ai-service<br/>Deployment (1 replica)"]
        end

        subgraph ns_infra["Namespace: infra"]
            KAFKA_ss["Kafka StatefulSet"]
            PG_ss["PostgreSQL StatefulSet"]
            REDIS_dep["Redis Deployment"]
        end

        INGRESS["Nginx Ingress<br/>TLS termination"]
    end

    Internet --> INGRESS
    INGRESS --> TAB_pod
    TAB_pod --> TE_pod
    TAB_pod --> SS_pod
    TAB_pod --> AI_pod
    MDS_pod --> KAFKA_ss
    KAFKA_ss --> MDS_pod
    TE_pod --> PG_ss
    SS_pod --> PG_ss
    AI_pod --> PG_ss
    AI_pod --> REDIS_dep
```

Each service is independently deployable. The `trading-engine` is horizontally scaled to absorb parallel optimisation requests. `market-data-service` runs as a singleton to avoid duplicate Kafka messages. The `ai-service` runs as a singleton to avoid concurrent learning loop executions.
