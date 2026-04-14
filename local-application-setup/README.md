# local-application-setup

> Docker Compose environment for local development — starts Kafka, PostgreSQL (with pgvector), Redis, and their management UIs with a single command.

---

## What's Included

| Service | Image | Port | Purpose |
|---|---|---|---|
| `zookeeper` | `confluentinc/cp-zookeeper:7.5.0` | `2181` | Kafka coordination |
| `kafka` | `confluentinc/cp-kafka:7.5.0` | `9092` | Message broker |
| `kafka-ui` | `provectuslabs/kafka-ui` | `8087` | Kafka topic browser |
| `postgres` | `pgvector/pgvector:pg16` | `5432` | Primary database (pgvector enabled) |
| `pgadmin` | `dpage/pgadmin4` | `5050` | PostgreSQL admin UI |
| `redis` | `redis:7-alpine` | `6379` | LRU cache (512 MB limit) |

The PostgreSQL image is `pgvector/pgvector:pg16` — this is critical because it includes the `vector` extension required by `ai-service` for cosine-similarity strategy memory search.

---

## Database Schema

The schema is applied automatically on first container startup via Docker's `entrypoint-initdb.d` mechanism:

```
local-application-setup/
└── sql/
    └── init.sql    ← Applied once on first postgres container start
```

Tables created:

| Table | Purpose |
|---|---|
| `market_data` | OHLCV candles from all exchange streams. Unique on `(exchange, symbol, interval, open_time)` |
| `strategies` | Strategy DSL definitions. Stored as JSONB; GIN-indexed for field-level queries |
| `backtest_results` | Simulation run outcomes linked to strategies |
| `strategy_memory` | pgvector store for AI RAG. `embedding vector(1536)` column with IVFFlat index |

Extensions enabled: `vector` (pgvector), `uuid-ossp`

---

## Starting the Environment

```bash
cd local-application-setup
docker compose up -d
```

Wait ~15 seconds for all services to become healthy, then verify:

```bash
# Check all containers are running
docker compose ps

# Confirm database tables were created
docker exec postgres psql -U trading -d trading -c "\dt"

# Confirm pgvector extension is active
docker exec postgres psql -U trading -d trading \
  -c "SELECT extname FROM pg_extension WHERE extname = 'vector';"
```

---

## Stopping the Environment

```bash
# Stop containers but preserve data volumes
docker compose stop

# Stop and destroy all data (full reset)
docker compose down -v
```

---

## Management UIs

| UI | URL | Credentials |
|---|---|---|
| Kafka UI | `http://localhost:8087` | None |
| pgAdmin | `http://localhost:5050` | admin@admin.com / admin |

**Connect pgAdmin to PostgreSQL:**
1. Open `http://localhost:5050`
2. Right-click Servers → Register → Server
3. Host: `postgres`, Port: `5432`, Database: `trading`, Username: `trading`, Password: `trading`

---

## Connection Details

### PostgreSQL

```
Host:     localhost
Port:     5432
Database: trading
Username: trading
Password: trading
JDBC:     jdbc:postgresql://localhost:5432/trading
R2DBC:    r2dbc:postgresql://localhost:5432/trading
```

### Kafka

```
Bootstrap servers: localhost:9092
(Internal Docker network: kafka:29092)
```

### Redis

```
Host: localhost
Port: 6379
Auth: none (dev only)
Max memory: 512 MB (allkeys-lru eviction)
```

---

## Resetting the Schema

If you need to apply schema changes after the initial setup:

```bash
# Connect to the database and run your SQL manually
docker exec -it postgres psql -U trading -d trading

# Or pipe a file
cat sql/your-migration.sql | docker exec -i postgres psql -U trading -d trading
```

> **Note:** The `init.sql` script uses `IF NOT EXISTS` throughout — re-running it is safe and will not drop or alter existing data.

---

## Troubleshooting

**Port already in use:**
```bash
# Find the process using port 5432
netstat -ano | findstr :5432   # Windows
lsof -i :5432                  # Linux/Mac
```

**pgvector extension not found:**
Make sure you are using the `pgvector/pgvector:pg16` image, not the plain `postgres:16` image. The plain image does not include the vector extension pre-installed.

**Kafka not reachable from services:**
Services inside Docker containers should use `kafka:29092` as the bootstrap server. Services running on the host machine should use `localhost:9092`. The `KAFKA_ADVERTISED_LISTENERS` in `docker-compose.yml` configures both.

**Database tables not created:**
The `init.sql` file is only applied on the very first container start (when the data volume is empty). If the container was started before the SQL file existed, run:
```bash
docker compose down -v   # destroys the volume
docker compose up -d     # recreates and runs init.sql
```
