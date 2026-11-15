Document these key decisions:

Transaction is the Aggregate Root.

Transaction owns all financial invariants.

State transitions happen only through domain methods.

Terminal states cannot transition further.

Expiry releases the remaining held balance.

Partial capture and partial release are supported.

All amounts must use the same currency.

Domain operations use an injected Clock.