# API, persistence and operational readiness

## Architecture review

The core remains hexagonal: the aggregate owns financial invariants; the application service coordinates use cases; repository, expiry and idempotency are ports. Spring MVC is the inbound adapter. JDBC is the outbound persistence adapter. Flyway owns schema evolution.

## Persistence

- payment_transaction stores the current aggregate snapshot.
- transaction_event stores ordered business-event history.
- Database constraints enforce non-negative balances and the accounting equation.
- Aggregate state and its event rows are written in one JDBC transaction.
- Event types are mapped explicitly; unrestricted polymorphic JSON deserialization is avoided.

## API and security

The API exposes create, hold, capture, release, expire and retrieve operations under /api/v1/transactions. Mutations require Idempotency-Key. Request validation rejects malformed currencies and non-positive amounts. Errors use Spring ProblemDetail responses. HTTP Basic authentication is enabled; PAYMENT_API_PASSWORD is mandatory at startup. Health and info are public; metrics are exposed and should remain behind an internal network boundary.

## Coverage

JaCoCo generates target/site/jacoco/index.html. The original 62-test core suite is retained. The JDBC adapter needs dedicated PostgreSQL integration tests for restart/replay, rollback and concurrent writers; do not infer adapter coverage from the existing test count.

## Production gaps

1. The included idempotency key scope is global to this sample API. A multi-tenant system must scope keys by authenticated merchant/account and define retention/expiry.
2. The outbox dispatcher provides at-least-once delivery. A crash after publishing but before marking an event published can produce duplicates; downstream consumers must deduplicate by outbox event ID.
3. The included publisher is a structured-log adapter, not Kafka/SNS or another durable broker integration. A real deployment must configure a broker publisher and consumer recovery policy.
4. Tenant-scoped authorization, rate limiting, secret rotation, TLS/deployment policy and security audit remain required.
5. Configure an OTLP collector endpoint, dashboards, SLOs and alert thresholds; tracing export is optional and depends on a reachable collector.
6. Provider integration, reconciliation, retention/cleanup jobs and operational recovery runbooks remain out of scope.

This is an educational reference runtime, not a production payment processor. Never use it to authorize, capture or move real funds.


## Coverage review (CI JaCoCo report)

The first CI report for this branch (JaCoCo 0.8.14, 24 classes) reports **82% instruction coverage and 72% branch coverage** overall. These figures include the original core tests plus the JDBC repository round-trip test.

| Area | Instruction coverage | Branch coverage | Review |
|---|---:|---:|---|
| Domain model | 95% | 86% | Strong baseline; add targeted boundary tests for expiry and terminal transitions |
| Application service | 88% | n/a | Add missing-transaction and failure-path tests for every use case |
| JDBC persistence | 90% | 60% | Add rollback, unknown event, missing-row and concurrent-writer tests |
| Domain events | 77% | 54% | Exercise invalid constructor inputs for every event record |
| Idempotency adapter | 100% | 87% | Add tests for key collision/failure/replay edge cases |
| REST API | 0% | n/a | No controller or exception-handler tests yet; highest-priority gap |
| Runtime configuration/security | 0% | 0% | Add application-context tests for missing credentials, configured credentials and authorization rules |

The REST API and runtime configuration are currently untested by the automated suite. The 82% aggregate figure therefore must not be interpreted as production-level coverage. The workflow publishes the full JaCoCo HTML report as the jacoco-report artifact for each run.


## Durable consistency implementation

Flyway V2 adds `idempotency_record`, `transaction_outbox`, and `payment_transaction.version`. `JdbcIdempotencyStore` starts a REQUIRED transaction, inserts/locks the idempotency row, executes the application operation, and stores the original response before commit. `JdbcTransactionRepository` joins that transaction, performs compare-and-set updates with `WHERE id = ? AND version = ?`, appends audit rows, and inserts matching outbox rows. A stale version raises an optimistic-lock exception mapped to HTTP 409. This keeps the aggregate, idempotency result, audit history and outbox record atomic.

The outbox dispatcher uses `FOR UPDATE SKIP LOCKED` to support multiple pollers. It retries failed publishes with bounded exponential delay. Delivery is at-least-once, not exactly-once. The included logging publisher demonstrates the adapter boundary; it must be replaced by a real broker publisher for integration with downstream services. Downstream consumers must be idempotent and deduplicate using the outbox ID.

## Observability

`X-Correlation-Id` is accepted when it matches a restricted character set, otherwise generated; it is echoed in the response and attached to logs via MDC. Logback emits JSON logs. Micrometer counters include `payment.idempotency.replay`, `payment.idempotency.conflict`, `payment.outbox.published` and `payment.outbox.failed`. Actuator exposes health, metrics and Prometheus; Micrometer Tracing is configured for OTLP export using `OTEL_EXPORTER_OTLP_ENDPOINT`.

## Integration tests

`JdbcTransactionRepositoryTest` verifies optimistic-lock rejection, aggregate/audit/outbox persistence and Flyway schema behavior. `PaymentApiPostgresIntegrationTest` runs the HTTP API against PostgreSQL Testcontainers and verifies idempotency replay, fingerprint conflicts and cross-load stale-write rejection. Docker is required to execute the PostgreSQL Testcontainers suite.
