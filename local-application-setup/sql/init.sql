-- =============================================================================
-- Trading Platform — PostgreSQL Init Script
-- Runs automatically on first container startup via docker-entrypoint-initdb.d/
-- Must be fully idempotent (IF NOT EXISTS everywhere).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector: ANN similarity search
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- uuid_generate_v4() fallback


-- ---------------------------------------------------------------------------
-- market_data
-- Raw OHLCV candles persisted from any exchange source.
-- Unique constraint on (exchange, symbol, interval, open_time) prevents
-- duplicate upserts from the market-data-service Kafka consumer.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS market_data (
    id           BIGSERIAL    PRIMARY KEY,
    exchange     VARCHAR(20)  NOT NULL,
    symbol       VARCHAR(20)  NOT NULL,
    interval     VARCHAR(5)   NOT NULL,
    open_time    TIMESTAMPTZ  NOT NULL,
    close_time   TIMESTAMPTZ  NOT NULL,
    open         NUMERIC(20,8),
    high         NUMERIC(20,8),
    low          NUMERIC(20,8),
    close        NUMERIC(20,8),
    volume       NUMERIC(30,8),
    trade_count  BIGINT,
    is_closed    BOOLEAN      DEFAULT false,

    CONSTRAINT market_data_uq UNIQUE (exchange, symbol, interval, open_time)
);

CREATE INDEX IF NOT EXISTS market_data_lookup_idx
    ON market_data (exchange, symbol, interval, open_time DESC);


-- ---------------------------------------------------------------------------
-- strategies
-- Stores both built-in and user/LLM-generated strategy definitions as JSONB.
-- source values: 'BUILTIN' | 'USER' | 'LLM' | 'EVOLVED'
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS strategies (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    source      VARCHAR(20)  NOT NULL
                    CHECK (source IN ('BUILTIN', 'USER', 'LLM', 'EVOLVED')),
    dsl         JSONB        NOT NULL,
    is_active   BOOLEAN      DEFAULT true,
    created_at  TIMESTAMPTZ  DEFAULT now(),
    updated_at  TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX IF NOT EXISTS strategies_source_idx  ON strategies (source);
CREATE INDEX IF NOT EXISTS strategies_active_idx  ON strategies (is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS strategies_dsl_gin_idx ON strategies USING gin (dsl);


-- ---------------------------------------------------------------------------
-- backtest_results
-- One row per simulation run, linked to the strategy that was tested.
-- market_context and result_detail store flexible metadata as JSONB.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS backtest_results (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_id             UUID         REFERENCES strategies(id) ON DELETE SET NULL,
    symbol                  VARCHAR(20),
    interval                VARCHAR(5),
    from_time               TIMESTAMPTZ,
    to_time                 TIMESTAMPTZ,
    total_trades            INT,
    win_rate                NUMERIC(5,2),
    total_pnl               NUMERIC(20,2),
    max_drawdown            NUMERIC(5,2),
    sharpe_ratio            NUMERIC(8,4),
    is_statistically_valid  BOOLEAN      DEFAULT false,
    validation_note         TEXT,
    market_context          JSONB,
    result_detail           JSONB,
    created_at              TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX IF NOT EXISTS backtest_strategy_idx
    ON backtest_results (strategy_id, created_at DESC);

CREATE INDEX IF NOT EXISTS backtest_symbol_interval_idx
    ON backtest_results (symbol, interval, created_at DESC);


-- ---------------------------------------------------------------------------
-- strategy_memory
-- Vector store for RAG / semantic similarity search over strategy performance.
-- embedding is vector(1536) — sized for OpenAI text-embedding-3-small or
-- any 1536-dim model. The IVFFlat index below is commented out intentionally:
-- it must be created AFTER the table has data (requires training rows).
--
-- To create it once data is loaded:
--   CREATE INDEX ON strategy_memory
--     USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
--
-- verdict values: 'STRONG' | 'ACCEPTABLE' | 'POOR'
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS strategy_memory (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_name    VARCHAR(100),
    strategy_id      UUID         REFERENCES strategies(id) ON DELETE SET NULL,
    document         TEXT         NOT NULL,
    embedding        vector(1536),
    sharpe_ratio     FLOAT,
    win_rate         FLOAT,
    max_drawdown     FLOAT,
    trade_count      INT,
    verdict          VARCHAR(20)
                         CHECK (verdict IN ('STRONG', 'ACCEPTABLE', 'POOR')),
    market_context   JSONB,
    created_at       TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX IF NOT EXISTS strategy_memory_strategy_idx
    ON strategy_memory (strategy_id);

CREATE INDEX IF NOT EXISTS strategy_memory_verdict_idx
    ON strategy_memory (verdict);

-- NOTE: IVFFlat ANN index — add manually after inserting enough rows:
-- CREATE INDEX ON strategy_memory
--   USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);