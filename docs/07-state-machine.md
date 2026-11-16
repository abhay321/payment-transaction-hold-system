# Transaction State Machine

## Aggregate rules

- `Transaction` is the aggregate root and owns all financial invariants.
- State transitions happen only through domain methods.
- Terminal states cannot transition further.
- Partial capture and partial release are supported.
- All amounts use the transaction currency.
- Expiry releases the remaining held balance.
- Domain operations use an injected `Clock`.

## State definitions

| State | Meaning | Terminal |
|---|---|---|
| CREATED | Transaction exists; no hold has been placed | No |
| HELD | Entire original amount is held | No |
| PARTIALLY_CAPTURED | Some held amount has been captured; a balance remains held | No |
| PARTIALLY_RELEASED | Some held amount has been released; a balance remains held | No |
| CAPTURED | No held balance remains because the remaining balance was captured | Yes |
| RELEASED | No held balance remains because the remaining balance was released | Yes |
| EXPIRED | The remaining held balance was released by expiry | Yes |

A terminal disposition describes how the outstanding held balance was resolved. It does not imply that the entire original amount was captured or released. For example, after releasing INR 300 and capturing the remaining INR 700, the final state is `CAPTURED`, with captured amount INR 700 and released amount INR 300.

## Allowed transitions

| Current state | Operation | Next state |
|---|---|---|
| CREATED | Hold | HELD |
| HELD | Partial capture | PARTIALLY_CAPTURED |
| HELD | Full capture | CAPTURED |
| PARTIALLY_CAPTURED | Capture remaining | CAPTURED |
| PARTIALLY_CAPTURED | Release remaining | RELEASED |
| HELD | Partial release | PARTIALLY_RELEASED |
| HELD | Full release | RELEASED |
| PARTIALLY_RELEASED | Capture remaining | CAPTURED |
| PARTIALLY_RELEASED | Release remaining | RELEASED |
| HELD | Expire | EXPIRED |
| PARTIALLY_CAPTURED | Expire | EXPIRED |
| PARTIALLY_RELEASED | Expire | EXPIRED |

## Financial invariants

The aggregate must always satisfy:

`originalAmount = capturedAmount + heldAmount + releasedAmount`

- All balances must be non-negative.
- Capture cannot exceed the remaining held amount.
- Release cannot exceed the remaining held amount.
- Capture and release amounts must be positive and use the transaction currency.
- A transaction with no remaining held balance cannot be captured, released, or expired again.

## Expiry

Expiry is assigned only while a hold is active. Expiry occurs when the operation clock instant is equal to or later than the configured expiry instant. Expiry releases the entire remaining held balance and transitions the transaction to `EXPIRED`.
