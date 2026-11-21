Payment Transaction Hold Management System

A Java 21 implementation of a payment gateway transaction lifecycle, demonstrating object-oriented design, clean architecture, design patterns, concurrency control, idempotency, and comprehensive testing.

1. Project Overview

Payment gateways often need to reserve an amount before completing a payment.

For example:

A customer initiates a payment of ₹1,000.
The payment gateway places a hold on ₹1,000.
The merchant captures ₹600.
The merchant captures another ₹200.
The remaining ₹200 is released.

This project models that lifecycle and enforces the business rules around it.

The project includes a Spring Boot HTTP adapter and PostgreSQL JDBC repository alongside the original in-memory learning adapters. It does not connect to banks or move real money.

2. Goals
Implement a transaction lifecycle.
Support full and partial capture.
Support full and partial release.
Prevent invalid state transitions.
Prevent duplicate operations using idempotency keys.
Handle concurrent operations safely.
Maintain an immutable transaction history.
Support configurable hold expiry.
Demonstrate modern Java 21 features.
Apply design patterns only where they solve an actual problem.
Maintain high-quality automated test coverage.
3. Technology Stack
Technology	Purpose
Java 21	Application language
Maven	Build and dependency management
JUnit 5	Unit and integration tests
Mockito	Mocking dependencies
AssertJ	Readable assertions
JaCoCo	Test coverage
Git	Version control

Spring Boot 3.5, Spring MVC, JDBC, Spring Security HTTP Basic, Actuator, Flyway and PostgreSQL are used by the optional runtime layer. The domain and application services remain plain Java.

4. Functional Requirements
Transaction creation
Create a transaction with a unique identifier.
Accept only positive monetary amounts.
Initialize transaction status to CREATED.
Hold
Place a hold on a CREATED transaction.
Record the hold timestamp and expiry.
Prevent holding an already held or terminal transaction.
Capture
Capture all or part of the held amount.
Support multiple partial captures.
Prevent capture beyond the remaining held balance.
Transition to CAPTURED when the remaining held balance is fully captured.
Release
Release all or part of the remaining held balance.
Prevent releasing more than the held balance.
Transition to RELEASED when the remaining held balance is fully released.
Prevent further capture or release after the transaction reaches a terminal state.
Idempotency
Repeated requests with the same key and same payload must not execute twice.
Reuse of the same key with a different payload must be rejected.
Expiry
Expire holds according to a configurable policy.
Release the remaining held balance when an eligible hold expires.
Transaction history
Record every successful state transition.
Record operation type, amount, timestamp, and resulting status.
5. Non-Functional Requirements
Correct monetary arithmetic.
Consistent transaction state.
Safe concurrent operations.
Testable business logic.
Low coupling between business and infrastructure layers.
Extensible expiry policies.
Clear exception handling.
6. Domain Model

A transaction contains:

Transaction ID
Original amount
Captured amount
Held amount
Released amount
Transaction status
Creation timestamp
Last updated timestamp
Expiry timestamp
Transaction event history
Monetary invariants

The following invariant must always hold:

originalAmount = capturedAmount + heldAmount + releasedAmount

All monetary amounts must be non-negative.

Capture is allowed only up to the held amount.

Release is allowed only up to the held amount.

These rules are enforced in the domain model rather than relying only on service-layer validation.

7. Transaction State Machine

Supported states:

CREATED
HELD
PARTIALLY_CAPTURED
CAPTURED
PARTIALLY_RELEASED
RELEASED
EXPIRED

Allowed transitions:

Current state	Operation	Next state
CREATED	Hold	HELD
HELD	Partial capture	PARTIALLY_CAPTURED
HELD	Full capture	CAPTURED
PARTIALLY_CAPTURED	Capture remaining	CAPTURED
PARTIALLY_CAPTURED	Release remaining	RELEASED
HELD	Partial release	PARTIALLY_RELEASED
HELD	Full release	RELEASED
PARTIALLY_RELEASED	Capture remaining	CAPTURED
PARTIALLY_RELEASED	Release remaining	RELEASED
HELD	Expire	EXPIRED
PARTIALLY_CAPTURED	Expire	EXPIRED
PARTIALLY_RELEASED	Expire	EXPIRED

Terminal states:

CAPTURED
RELEASED
EXPIRED

A terminal disposition describes how the outstanding held balance was resolved. It does not imply that the entire original amount was captured or released. For example, after releasing INR 300 and capturing the remaining INR 700, the final state is CAPTURED, with captured amount INR 700 and released amount INR 300.

8. Architecture

The project follows a lightweight Hexagonal Architecture.

Domain layer

Contains business entities, value objects, events, state transitions and domain exceptions.

It must not depend on repositories, databases or application frameworks.

Application layer

Contains use cases and orchestration logic.

Examples:

CreateTransaction
HoldTransaction
CaptureTransaction
ReleaseTransaction
ExpireTransaction
Ports

Define contracts for external dependencies.

Examples:

TransactionRepository
IdempotencyStore
ExpiryPolicy
Infrastructure layer

Provides implementations of those contracts.

Examples:

InMemoryTransactionRepository
InMemoryIdempotencyStore
FixedDurationExpiryPolicy

This separation allows the storage implementation to change without rewriting business rules.

9. Design Patterns
Repository Pattern

Provides an abstraction over transaction persistence.

Why:

The service should not know how transactions are stored.
In-memory storage can be replaced with database persistence.
Tests can use a fake repository.
Factory Pattern

Centralizes transaction construction and identifier generation.

Why:

Prevents scattered construction logic.
Provides a single place to initialize domain defaults.
Supports future creation rules.
Strategy Pattern

Used for configurable expiry policies.

Why:

Different merchants may require different expiry durations.
Expiry logic can change independently of transaction orchestration.
New policies can be introduced without modifying existing service logic.
State Machine

Controls valid transaction lifecycle transitions.

Why:

Invalid operations must be rejected.
State changes must preserve financial invariants.
The lifecycle should be explicit and testable.
Dependency Injection

Dependencies are passed through constructors.

Why:

Avoids hard-coded implementations.
Simplifies testing.
Reduces coupling.

10. SOLID Principles
Single Responsibility Principle

Each class has one primary responsibility.

For example:

Transaction owns transaction invariants.
TransactionService orchestrates use cases.
Repository handles persistence.
ExpiryPolicy calculates expiry.
Open/Closed Principle

New expiry strategies can be added without modifying the transaction service.

Liskov Substitution Principle

Implementations of repository and policy interfaces must honor their declared contracts.

Interface Segregation Principle

Interfaces should expose only the operations their consumers require.

Dependency Inversion Principle

Application logic depends on repository and policy abstractions, not concrete infrastructure implementations.

11. Java 21 Features
Records

Used for immutable request and response DTOs.

Sealed interfaces

Used where a closed set of domain event types is useful.

Pattern matching for switch

Used for concise handling of known domain event types.

Switch expressions

Used for exhaustive state-related decisions.

Clock injection

Allows deterministic time-based tests without sleeping.

Virtual threads

An optional experiment for concurrent blocking workloads. They do not replace transaction locking or database consistency.

12. Concurrency

The in-memory implementation must protect individual transaction operations from conflicting concurrent updates.

A transaction-level lock ensures that two operations cannot simultaneously modify the same transaction.

For production database persistence, equivalent consistency must be maintained with transactions, optimistic locking or database locking.

A ConcurrentHashMap alone does not make mutations of the objects stored inside it thread-safe.

13. Idempotency

An idempotency key identifies a logical client request.

The system stores:

Idempotency key
Request fingerprint
Operation result

The same key and same request returns the original result.

The same key and different request is rejected.

In production, idempotency records and transaction updates must be committed atomically.

14. Testing Strategy
Domain tests
Valid transitions.
Invalid transitions.
Partial captures.
Partial releases.
Amount invariants.
Terminal states.
Application tests
Complete transaction workflows.
Missing transaction.
Invalid request.
Idempotency behavior.
Expiry behavior.
Concurrency tests
Simultaneous captures.
Capture versus release.
Independent transactions.
No negative held balance.
Time-based tests
Expired holds.
Non-expired holds.
Boundary timestamp behavior.

15. Running the Application

Requirements:

JDK 21
Maven 3.9+

Run tests:

bash
mvn clean test


Generate coverage:

bash
mvn clean verify


The JaCoCo report is generated under:

target/site/jacoco/index.html

16. Architecture Trade-offs
Why in-memory storage?

It keeps the exercise focused on business logic and design.

Limitation: data is lost when the application stops.

Why no Spring Boot?

The purpose is to demonstrate Java and OOP fundamentals without framework overhead.

Why BigDecimal?

Floating-point arithmetic is inappropriate for exact monetary values.

Why transaction-level locking?

It allows independent transactions to progress concurrently instead of serializing every operation with a global lock.

Why an explicit state machine?

It makes lifecycle rules visible and testable.

17. Production Considerations

A production payment platform would additionally require:

Durable database storage.
Atomic persistence of transaction and idempotency records.
Distributed concurrency control.
Authentication and authorization.
Audit logging.
Reconciliation.
Retry and failure recovery.
Payment-provider integration.
Monitoring and alerting.
Encryption and sensitive-data handling.

The reference runtime demonstrates selected controls, but production deployment still requires durable idempotency, distributed concurrency control, tenant authorization, reconciliation, provider integration, operational runbooks and security review. Do not use this sample to process real funds.

18. Interview Questions
Why is BigDecimal preferred over double?
Why use a repository abstraction?
Why use a Strategy pattern for expiry?
How do you prevent invalid state transitions?
How do you support partial capture?
How do you prevent duplicate capture requests?
What happens when capture and release execute simultaneously?
Why is ConcurrentHashMap not sufficient for thread safety?
How would you move this implementation to PostgreSQL?
How would you handle a crash after an external payment provider captures funds but before the local system records the result?

19. Learning Outcomes

This repository demonstrates:

Core Java 21.
Object-oriented design.
SOLID principles.
Design patterns.
Domain modeling.
Hexagonal Architecture.
Financial amount handling.
State machines.
Concurrency.
Idempotency.
Unit and integration testing.
Technical documentation and architecture decisions.


20. Application API Example

The application layer is exposed through `PaymentTransactionService`. Construct it with ports and infrastructure adapters; no Spring container is required.

```java
Clock clock = Clock.systemUTC();

PaymentTransactionService service = new PaymentTransactionService(
    new InMemoryTransactionRepository(),
    new InMemoryIdempotencyStore(),
    new FixedDurationExpiryPolicy(Duration.ofMinutes(30)),
    clock
);

TransactionSnapshot created =
    service.create(Money.of("1000.00", "INR"), "create-order-123");

TransactionSnapshot held =
    service.hold(created.id(), "hold-order-123");

TransactionSnapshot captured =
    service.capture(
        created.id(),
        Money.of("600.00", "INR"),
        "capture-part-1-order-123"
    );

TransactionSnapshot released =
    service.release(
        created.id(),
        Money.of("400.00", "INR"),
        "release-rest-order-123"
    );
```

The service returns immutable `TransactionSnapshot` values. Replaying an idempotency key with the same operation fingerprint returns the original snapshot; using that key for a different operation or payload is rejected.

The original domain/application suite contains 62 tests. Run `mvn clean verify` to compile, execute tests and generate the JaCoCo report. Coverage HTML is written to `target/site/jacoco/index.html`.


21. HTTP API and PostgreSQL runtime

Start PostgreSQL with docker compose up -d db, set PAYMENT_API_PASSWORD to a strong secret, and run mvn spring-boot:run. The API listens on port 8080. HTTP Basic authentication is required for transaction endpoints; /actuator/health and /actuator/info are public. Configure DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, PAYMENT_API_USERNAME, PAYMENT_API_PASSWORD and PAYMENT_HOLD_DURATION through the environment. Never commit credentials.

Endpoints: POST /api/v1/transactions; POST /api/v1/transactions/{id}/hold; POST /api/v1/transactions/{id}/capture; POST /api/v1/transactions/{id}/release; POST /api/v1/transactions/{id}/expire; GET /api/v1/transactions/{id}. Mutations require an Idempotency-Key header.

Durability and consistency: PostgreSQL stores idempotency keys and serialized operation results. The idempotency record, transaction aggregate, audit history and outbox event are committed in one database transaction. A database version column enforces optimistic locking across application instances; stale writes return HTTP 409. A polling outbox dispatcher publishes events at least once. The included publisher writes structured logs as a local demonstration adapter; replace it with a durable broker publisher and make consumers deduplicate by outbox event ID before using a real event bus. Request correlation IDs are returned in X-Correlation-Id and included in JSON logs. Micrometer tracing can export OTLP traces, and Actuator exposes health, metrics and Prometheus endpoints.


22. Durable consistency and operations

The V2 Flyway migration adds the idempotency ledger, optimistic-lock version, and transactional outbox. Idempotency keys are globally unique within this single-tenant sample; a multi-tenant deployment must scope keys by authenticated merchant/account. Failed use cases roll back the idempotency placeholder together with aggregate and event writes. Replays return the original stored snapshot; reusing a key with a different fingerprint returns HTTP 409.

The outbox dispatcher uses row-level locking with SKIP LOCKED so multiple instances can poll concurrently. Delivery is at-least-once: a process crash after external publication but before marking the row published can cause a duplicate. Consumers must deduplicate using the stable outbox event ID. The included logging publisher is only a local adapter, not a message broker integration. Failed publishes use bounded exponential retry delay and retain the last error.

The PostgreSQL Testcontainers suite covers API idempotency replay and conflict, durable outbox writes, and stale aggregate rejection. Run `mvn clean verify`; Docker must be available for the PostgreSQL integration suite. The API remains an educational reference and must not process real funds. Production use additionally requires tenant authorization, secrets management, TLS/network policy, broker integration, consumer deduplication, retention/cleanup policies, alerting, reconciliation and security review.


23. Multi-tenant security and isolation

The HTTP API resolves the merchant from the authenticated `MerchantPrincipal`. Clients cannot select a merchant through request bodies, query parameters or headers. Transaction reads and writes include the authenticated merchant ID in their database predicates; an unknown or cross-merchant transaction is reported as 404 to avoid disclosing another merchant's resource.

Idempotency is scoped by the pair `(merchant_id, idempotency_key)`. The same client key may be used independently by two merchants without replaying or conflicting with the other merchant's operation. Existing single-tenant rows are migrated to the `default` merchant by Flyway V3.

Roles:
- `MERCHANT_ADMIN`: read and mutate transactions.
- `MERCHANT_OPERATOR`: read and mutate transactions.
- `MERCHANT_READ_ONLY`: read transactions only.

For local/demo startup, configure `PAYMENT_MERCHANT_USERS` as semicolon-separated entries in the form `merchant-id|username|password|ROLE`. Example (use unique strong secrets in your environment):

```bash
export PAYMENT_MERCHANT_USERS='merchant-a|merchant-a-api|<strong-secret-at-least-16-chars>|MERCHANT_ADMIN;merchant-b|merchant-b-api|<another-strong-secret>|MERCHANT_OPERATOR;merchant-a|merchant-a-reader|<reader-secret-at-least-16-chars>|MERCHANT_READ_ONLY'
```

Passwords are BCrypt-hashed before storage in `merchant_user`; they are not written to logs. If `PAYMENT_MERCHANT_USERS` is absent, the legacy `PAYMENT_API_USERNAME` and `PAYMENT_API_PASSWORD` variables bootstrap a `default` merchant admin for backward-compatible local use. Do not use environment-based bootstrap as a production identity lifecycle; use a managed identity provider, secret manager, provisioning workflow, rotation and revocation policy.

Security audit records cover successful merchant authentication, failed authentication attempts and successful transaction actions. Records include merchant, subject, action, transaction ID and correlation ID where available; they deliberately exclude passwords, authorization headers and payment request payloads.
