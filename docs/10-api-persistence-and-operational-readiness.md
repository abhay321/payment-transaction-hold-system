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
