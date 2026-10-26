# Konto

A small financial-services backend: a double-entry ledger with idempotent, FX-aware transfers, plus a quantitative finance toolkit.

**Stack:** Java 21, Spring Boot 3.3, Spring Data JPA / Hibernate, MariaDB (H2 in MariaDB mode for tests).

## Run

```bash
docker compose up -d        # MariaDB on :3306
mvn spring-boot:run
mvn test                    # uses in-memory H2, no database needed
```

Connection settings come from `DB_URL`, `DB_USER`, `DB_PASSWORD` (defaults match `docker-compose.yml`).

## What's in it

**Ledger (`account`, `ledger`, `fx`)**
- Accounts hold `BigDecimal` balances (scale 4) with an `@Version` column.
- `POST /api/transfers` moves money atomically. Rows are locked with `PESSIMISTIC_WRITE` in ascending id order, so opposing concurrent transfers cannot deadlock.
- The `Idempotency-Key` header makes retries safe: a replay returns the original transfer; the same key with different parameters is a 409.
- Cross-currency transfers convert through a USD pivot and record the rate used.
- Every movement appends an immutable `LedgerEntry` with the running balance.
- `GET /api/audit/integrity` recomputes each balance from the ledger and reports any account that disagrees.

**Quant toolkit (`quant`)**
| Endpoint | Function |
|---|---|
| `POST /api/quant/loan-schedule` | Amortization schedule, `BigDecimal` throughout, final instalment clears rounding drift |
| `POST /api/quant/xirr` | Annualised return for irregularly dated cash flows (bracketed bisection) |
| `POST /api/quant/black-scholes` | Option price and delta, gamma, vega, theta, rho |
| `POST /api/quant/implied-volatility` | Inverts Black-Scholes from a market price |
| `POST /api/quant/value-at-risk` | Monte Carlo VaR and Expected Shortfall under GBM, run across virtual threads; seeded runs are reproducible. Give a volatility, or recent `dailyReturns` and the filter below supplies one |
| `POST /api/quant/volatility-forecast` | Hidden-volatility estimate and next-day forecast from daily returns, using a bootstrap particle filter (below) |

### Volatility forecasting

`dev.konto.volatility` is a Java port of the [Undertow](https://github.com/sofiaksoder/undertow) particle filter
(`pfvol/model.py`, `pfvol/filter.py`): a stochastic volatility model whose hidden log-variance is inferred from daily
returns, with optional jump and Student-t robustness variants. Defaults are the parameters Undertow fitted on SPY
2010-2018 (phi 0.94, sigma_eta 0.35, mu -9.86).

`ParticleFilterTest` runs the Java filter on the same 250-day series as Undertow's `tests/data/parity.json` and checks
it against the Python output (100k particles): filtered volatility within 0.8% and log-likelihood within 0.03.
Random streams differ between NumPy and Java, so agreement is within Monte Carlo error, not bit-for-bit.

```bash
curl -X POST localhost:8080/api/quant/volatility-forecast -H 'Content-Type: application/json' \
  -d '{"returns":[0.004,-0.012, ...at least 20 daily returns as decimals...],"particles":5000,"jump":true}'
```

Not ported: regime-switching simulation, GARCH and rolling-std benchmarks, backtesting and the Diebold-Mariano test.

## Web UI

Open http://localhost:8080/ once the app is running. It implements the Alpine design handoff (light mode; desktop first,
with a top bar and stacked layouts on phones): accounts overview, statement, a four-step send-money flow, the loan,
investment-return, option and implied-volatility calculators, the risk simulator, and a health check.

It is plain HTML, CSS and ES modules in `src/main/resources/static` (no build step), served by Spring Boot. Two details
worth knowing:

- The send-money flow creates one `Idempotency-Key` when you reach the confirm step and reuses it for every retry.
- The risk simulator's histogram is the real distribution of simulated losses (`histogram`, `lossLow`, `lossHigh` in the
  value-at-risk result), not an illustrative curve. The "Estimate volatility from recent returns" box calls the
  particle-filter forecast.

Other endpoints added for the UI: `GET /api/accounts`, `GET /api/fx/rate?from=USD&to=EUR` (the send-money preview) and a
`counterpartyAccountId` on each statement row.

## Try it

```bash
curl -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"owner":"alice","currency":"USD","initialDeposit":1000}'
curl -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"owner":"bob","currency":"EUR","initialDeposit":0}'
curl -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":100}'
curl localhost:8080/api/audit/integrity

curl -X POST localhost:8080/api/quant/value-at-risk -H 'Content-Type: application/json' \
  -d '{"portfolioValue":1000000,"annualDrift":0.07,"annualVolatility":0.2,"horizonDays":10,"confidence":0.99,"paths":1000000,"seed":42}'
```

## Notes

- Schema is created by Hibernate (`ddl-auto: update`) for simplicity; a production setup would use Flyway migrations.
- FX rates are a static table in `FxService`; swap in a market-data feed.
