# Contributing

Thank you for your interest in contributing to the Crypto Trading Assistant. This document covers the development environment setup, branch strategy, commit conventions, and PR process.

---

## Development Environment Setup

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 | Use [SDKMAN](https://sdkman.io/) or download from [jdk.java.net](https://jdk.java.net/) |
| Maven | 3.9+ | Or use the `./mvnw` wrapper included in each service |
| Docker Desktop | 24+ | Required for local infrastructure |
| Node.js | 20 LTS | For the frontend only |
| Git | 2.40+ | |

### Step-by-step setup

```bash
# 1. Clone
git clone https://github.com/<your-username>/crypto-trading-app.git
cd crypto-trading-app

# 2. Copy and fill environment variables
cp .env.example .env
# Required: ANTHROPIC_API_KEY
# Optional: BINANCE_API_KEY, BINANCE_API_SECRET (only for private endpoints)

# 3. Start infrastructure
cd local-application-setup
docker compose up -d
cd ..

# 4. Verify the database schema was applied
docker exec postgres psql -U trading -d trading -c "\dt"
# Should list: market_data, strategies, backtest_results, strategy_memory

# 5. Build all services
for svc in trading-engine strategy-service market-data-service ai-service trading-assistant-backend; do
  echo "Building $svc..."
  (cd $svc && ./mvnw -q clean package -DskipTests)
done

# 6. Install frontend dependencies
cd frontend && npm install && cd ..
```

### IDE setup (IntelliJ IDEA recommended)

1. Open the root folder as a project.
2. Import each service as a Maven module (`File → New → Module from Existing Sources`).
3. Set the Project SDK to Java 25.
4. Enable annotation processing for Lombok (`Settings → Build → Compiler → Annotation Processors`).
5. Install the Lombok plugin if not already installed.

---

## Branch Naming Conventions

Use the following prefix scheme:

| Prefix | When to use | Example |
|---|---|---|
| `feat/` | New feature or capability | `feat/strategy-leaderboard-api` |
| `fix/` | Bug fix | `fix/kafka-consumer-reconnect` |
| `refactor/` | Code restructure with no behaviour change | `refactor/backtesting-service-extract` |
| `chore/` | Build, config, dependency updates | `chore/upgrade-spring-boot-4.1` |
| `docs/` | Documentation only | `docs/trading-engine-readme` |
| `test/` | Adding or improving tests | `test/backtest-service-edge-cases` |
| `hotfix/` | Urgent production fix | `hotfix/memory-leak-websocket` |

Branch names should be lowercase, hyphen-separated, and descriptive. Avoid generic names like `fix/bug` or `feat/stuff`.

---

## Commit Message Format

This project follows the **Conventional Commits** specification.

```
<type>(<scope>): <short summary>

[optional body]

[optional footer(s)]
```

### Types

| Type | When to use |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding missing tests or correcting existing tests |
| `docs` | Documentation changes only |
| `chore` | Dependency updates, build config, CI changes |
| `perf` | Performance improvements |
| `style` | Formatting, missing semicolons — no logic changes |

### Scopes

Use the service name as scope: `trading-engine`, `market-data`, `strategy-service`, `ai-service`, `frontend`, `infra`.

### Examples

```
feat(trading-engine): add MACD strategy with custom signal line period

fix(market-data): handle Binance WebSocket ping/pong to prevent silent disconnects

chore(infra): upgrade kafka to 7.6.0

docs(ai-service): document learning loop step sequence

test(trading-engine): add edge case for empty kline list in BacktestingService
```

### Rules
- **Subject line ≤ 72 characters**
- Use the **imperative mood** ("add feature" not "added feature")
- Do not end the subject line with a period
- Reference issue numbers in the footer: `Closes #42` or `Related to #17`

---

## Running Tests

Each service has a test suite runnable with Maven:

```bash
# Run tests for a single service
cd trading-engine
./mvnw test

# Run tests with coverage report
./mvnw verify

# Run a specific test class
./mvnw test -Dtest=BacktestingServiceTest

# Run all services' tests from root
for svc in trading-engine strategy-service market-data-service ai-service trading-assistant-backend; do
  echo "Testing $svc..."
  (cd $svc && ./mvnw test -q)
done
```

**Integration tests** that require PostgreSQL or Kafka should be tagged with `@Tag("integration")` and require the Docker Compose stack to be running.

### Frontend tests

```bash
cd frontend
npm test
```

---

## PR Checklist

Before opening a pull request, verify all of the following:

**Code quality**
- [ ] All tests pass locally (`./mvnw test`)
- [ ] No new compiler warnings introduced
- [ ] Lombok annotations used appropriately (no manual getters/setters for simple value classes)
- [ ] No hardcoded credentials, API keys, or connection strings in source files
- [ ] New environment variables added to `.env.example` with placeholder values

**Design**
- [ ] New REST endpoints follow existing path conventions (`/api/v1/...` for `trading-engine`, `/api/strategies/...` for `strategy-service`, etc.)
- [ ] New Kafka topics documented in the relevant service README
- [ ] Database schema changes include a migration SQL snippet in the PR description
- [ ] No `@SuppressWarnings` added to hide real issues

**Documentation**
- [ ] Public API changes reflected in the service README's API reference table
- [ ] New configuration properties added to the service README's Configuration table
- [ ] Significant design decisions explained in a PR comment or ADR

**Observability**
- [ ] New business logic paths include `log.info(...)` or `log.debug(...)` at appropriate levels
- [ ] Error paths log with `log.error(...)` and include exception context
- [ ] No `System.out.println` or `e.printStackTrace()` in production code

---

## Code Style

- **Indentation:** 4 spaces (Java), 2 spaces (JSON/YAML/HTML)
- **Line length:** ≤ 120 characters
- **Imports:** No wildcard imports; organised by IDE (static imports last)
- **Naming:** `camelCase` for variables and methods, `PascalCase` for classes, `UPPER_SNAKE_CASE` for constants
- **Records vs classes:** Prefer Java `record` for pure data carriers with no mutable state
- **Optional:** Do not use `Optional` as method parameters; only as return types
- **Null handling:** Prefer `@NonNull` annotations and early returns over deep null checks

---

## Reporting Issues

When filing a bug report, include:
1. The service name and version (check `pom.xml`)
2. Steps to reproduce
3. Expected vs actual behaviour
4. Relevant log output (redact any sensitive values)
5. Your OS and Java version (`java -version`)
