# Application layer and idempotency

## Application service

`PaymentTransactionService` coordinates transaction creation, hold, capture, release and expiry. It depends only on abstractions for transaction persistence, idempotency and expiry calculation, plus an injected `Clock`.

The service returns `TransactionSnapshot`, an immutable application response containing the balances, status, timestamps, expiry and a copied event list. This is important for idempotency: a replay returns the result as it was when the original request completed, even if the underlying aggregate has subsequently changed.

## Ports and adapters

- `TransactionRepository`: save and retrieve transaction aggregates.
- `IdempotencyStore`: execute a request once for a key/fingerprint pair.
- `ExpiryPolicy`: calculate a hold expiry instant.
- `InMemoryTransactionRepository`: process-local aggregate storage.
- `InMemoryIdempotencyStore`: process-local idempotency result storage.
- `FixedDurationExpiryPolicy`: expiry strategy based on a positive duration.

## Concurrency model

The transaction aggregate synchronizes its mutating methods and state accessors. This prevents two in-process operations from observing and applying conflicting changes to the same aggregate at the same time. The in-memory repository uses a `ConcurrentHashMap` to safely publish aggregate references; the map does not replace aggregate-level synchronization.

The idempotency adapter uses `ConcurrentHashMap.compute` so requests using the same key are serialized and requests with different keys need not share a global lock.

## Important limitations

These adapters are deliberately process-local. Data and idempotency records disappear on restart. They do not provide cross-process locking or atomic commits between transaction state and idempotency results. A production implementation needs durable storage and an atomic transaction boundary, typically with database transactions and optimistic or pessimistic locking.

The sample is for design learning and machine-coding practice. It is not a payment processor and must not be connected to real money movement.
