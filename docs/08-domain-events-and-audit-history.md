Domain Events and Transaction Audit History
1. Objective

The transaction aggregate maintains the current financial state of a payment transaction.

However, the current state alone does not explain how the transaction reached that state.

Domain events provide an immutable record of successful business operations.

2. What is a Domain Event?

A domain event represents an important business fact that has already occurred.

Examples:

TransactionCreated
HoldPlaced
AmountCaptured
AmountReleased
TransactionExpired

Events use past-tense names because they describe completed business outcomes.

3. Command vs Event

A command requests an operation.

An event records the successful outcome of an operation.

Examples:

CaptureTransaction: command.
AmountCaptured: event.

A command may fail validation. A corresponding success event must not be generated when the operation fails.

4. Implementation

The TransactionEvent sealed interface defines the permitted transaction event types.

Individual events are implemented as Java records.

Records are suitable because event data should be immutable.

5. Why Sealed Interfaces?

Sealed interfaces restrict which classes may implement an interface.

This makes the supported transaction event types explicit and allows exhaustive pattern matching.

6. Why Records?

Records provide concise immutable data carriers.

They automatically implement equality, hashing and string representation.

This reduces boilerplate and makes event definitions easier to maintain.

7. Event History

The Transaction aggregate maintains an ordered event history.

External callers receive an immutable snapshot using List.copyOf().

This prevents external modification of the aggregate's internal history.

8. Domain Events vs Event Sourcing

This project uses domain events as an audit history.

It does not implement Event Sourcing.

The current Transaction state is maintained directly by the aggregate.

In Event Sourcing, the event stream itself is the primary source of truth and current state is reconstructed by replaying events.

9. Production Considerations

The initial implementation keeps events in memory.

A production system must persist transaction updates and audit events atomically.

An outbox pattern can help ensure that events are reliably published to external messaging infrastructure after a successful database transaction.

10. Design Trade-offs

Benefits:

Better traceability.
Clear audit history.
Immutable historical facts.
Separation between business changes and downstream consumers.

Limitations:

In-memory history is lost after application restart.
Event storage can grow over time.
Durable event publication requires additional infrastructure.