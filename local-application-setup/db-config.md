# Database & Cache Configuration Reference

Reference for connecting new services to the PostgreSQL and Redis instances
started by `kafka-compose.yml`.

---

## PostgreSQL

| Property       | Value                                          |
|----------------|------------------------------------------------|
| Image          | `postgres:16-alpine`                           |
| Host           | `localhost` (from host) / `postgres` (Docker)  |
| Port           | `5432`                                         |
| Database       | `trading`                                      |
| Username       | `trading`                                      |
| Password       | `trading`                                      |
| pgvector       | enabled via `CREATE EXTENSION IF NOT EXISTS vector` |

### JDBC URL

```
jdbc:postgresql://localhost:5432/trading
```

### R2DBC URL (reactive services)

```
r2dbc:postgresql://localhost:5432/trading
```

### Spring Boot datasource snippet

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/trading
    username: trading
    password: trading
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # schema managed by init.sql, never auto-create
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

### Maven dependency (Java 21 services)

```xml
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>

<!-- pgvector JDBC type support -->
<dependency>
  <groupId>com.pgvector</groupId>
  <artifactId>pgvector</artifactId>
  <version>0.1.6</version>
</dependency>
```

---

## Redis

| Property  | Value         |
|-----------|---------------|
| Image     | `redis:7-alpine` |
| Host      | `localhost` (from host) / `redis` (Docker) |
| Port      | `6379`        |
| Max memory | `512 MB`     |
| Eviction  | `allkeys-lru` |
| Auth      | none (dev only) |

### Spring Boot cache snippet

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### Maven dependency

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## Schema — Tables Created by `sql/init.sql`

| Table              | Purpose                                                    |
|--------------------|------------------------------------------------------------|
| `market_data`      | Persisted OHLCV candles (exchange + symbol + interval + time) |
| `strategies`       | Strategy DSL definitions (BUILTIN / USER / LLM / EVOLVED) |
| `backtest_results` | Simulation run outcomes linked to strategies               |
| `strategy_memory`  | Vector store for RAG / semantic strategy similarity search |

---

## Services That Will Connect

| Service                       | Java Version | DB Access        | Notes                          |
|-------------------------------|-------------|------------------|--------------------------------|
| `market-data-service`         | Java 21     | Write (JDBC)     | Persist candles from Kafka     |
| `trading-engine`              | Java 25     | Read/Write (R2DBC) | Save backtest results        |
| New: `strategy-intelligence`  | **Java 21** | Read/Write (JDBC + pgvector) | RAG + LLM strategy gen |

> **Java version policy:** All new services must target **Java 21** (LTS).
> Do not introduce Java 25 in new services — it is non-LTS and only used in
> the already-stable `trading-engine`.

---

## IVFFlat Index — When to Create

The `strategy_memory.embedding` column uses `vector(1536)`.
The ANN index must be created **after** inserting training data (minimum ~1000 rows):

```sql
CREATE INDEX ON strategy_memory
  USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

Run this manually or via a migration once the table has sufficient rows.
Before the index exists, pgvector falls back to an exact sequential scan —
correct but slower.

---

## Starting Everything

```bash
cd local-application-setup
docker compose -f kafka-compose.yml up -d
```

### Verify

```bash
# All 4 tables visible
psql -h localhost -U trading -d trading -c "\dt"

# pgvector extension confirmed
psql -h localhost -U trading -d trading \
  -c "SELECT extname FROM pg_extension WHERE extname='vector';"

# Kafka still healthy
docker compose -f kafka-compose.yml ps
```