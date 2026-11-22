# load-tests

[Gatling](https://gatling.io/) simulations of the order service. They run against a started stack, never as part of
`mvn verify`.

## CreateOrderSimulation

Creates orders through `POST /api/v1/orders` at a request rate raised in steps, and fails the run when more than 1%
of the requests fail or the 95th percentile of the response time is above 500 ms.

Start the servlet stack with the order service's global and per-client rate limits raised - a load test sends every
request from one address, and the defaults (100 and 20 requests a second) would answer most of them with `429`:

```bash
ORDER_RATE_LIMIT_GLOBAL_CAPACITY=1000 ORDER_RATE_LIMIT_PER_IP_CAPACITY=1000 \
  docker compose -f docker/compose.servlet.yml up -d --wait
```

Then run the simulation from the repository root:

```bash
./mvnw -pl load-tests gatling:test -DbaseUrl=http://localhost:8180
```

The report is written to `load-tests/target/gatling/`.

| System property | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | the order service |
| `startRps` | `10` | requests a second of the first step |
| `stepRps` | `10` | added on every next step |
| `steps` | `5` | number of steps |
| `stepSeconds` | `60` | how long each step is held |
| `rampSeconds` | `10` | ramp into each step |
| `customerPoolSize` | `1000` | customer ids the orders are spread over |
| `maxFailedPercent` | `1.0` | failed requests allowed, in percent |
| `p95Ms` | `500` | 95th percentile of the response time allowed, in ms |
