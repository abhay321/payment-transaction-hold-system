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

The implementation is an in-memory application intended for machine coding interviews and software design learning. It does not connect to banks or move real money.

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

No Spring Boot is required. The initial application uses plain Java to demonstrate core programming and architectural concepts.

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
Version
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

These are deliberately outside the initial in-memory machine coding implementation.

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
