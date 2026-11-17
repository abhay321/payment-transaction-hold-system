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

1. Idempotency remains process-local and is lost on restart. Persist idempotency records and commit them atomically with aggregate state.
2. Service synchronization is JVM-local. Add optimistic versioning or a lock held across the complete use case for multi-instance deployments.
3. Add PostgreSQL Testcontainers tests for round-trip, rollback and concurrency.
4. Add tenant-scoped authentication/authorization, rate limiting, secret rotation and security audit.
5. Add structured logs, correlation IDs, metrics and alert thresholds.
6. Provider integration, reconciliation, outbox delivery and recovery runbooks remain out of scope.

This is an educational reference runtime, not a production payment processor. Never use it to authorize, capture or move real funds.
