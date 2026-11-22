# load-tests

[Gatling](https://gatling.io/) simulations of the order service. They run against a started stack, never as part of
`mvn verify`.

## CreateOrderSimulation

Creates orders through `POST /api/v1/orders` under one of three load profiles, and fails the run when more than 1%
of the requests fail or the 95th percentile of the response time is above 500 ms.

Start the servlet stack with the order service's global and per-client rate limits raised - a load test sends every
request from one address, and the defaults (100 and 20 requests a second) would answer most of them with `429`:

```bash
ORDER_RATE_LIMIT_GLOBAL_CAPACITY=1000 ORDER_RATE_LIMIT_PER_IP_CAPACITY=1000 \
  docker compose -f docker/compose.servlet.yml up -d --wait
```

Then run the simulation from the repository root, picking the profile:

```bash
./mvnw -pl load-tests gatling:test -DbaseUrl=http://localhost:8180 -Dprofile=steady
```

The report is written to `load-tests/target/gatling/`.

### Profiles

| Profile | Load | Properties (defaults) | Answers |
|---|---|---|---|
| `smoke` (default) | a constant rate from the first second | `rps` (5), `durationSeconds` (30) | does the service answer under a light load |
| `steady` | a ramp to a constant rate, then held | `rps` (50), `rampSeconds` (10), `durationSeconds` (300) | the latency of a sustained load |
| `peak` | a rate raised in steps | `startRps` (10), `stepRps` (10), `steps` (5), `rampSeconds` (10), `stepSeconds` (60) | the rate at which the latency starts to climb |

The default `smoke` run takes half a minute and stays under the default rate limits, so it needs no raised limits.

### Common properties

| System property | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | the order service |
| `profile` | `smoke` | `smoke`, `steady` or `peak` |
| `customerPoolSize` | `1000` | customer ids the orders are spread over |
| `maxFailedPercent` | `1.0` | failed requests allowed, in percent |
| `p95Ms` | `500` | 95th percentile of the response time allowed, in ms |
