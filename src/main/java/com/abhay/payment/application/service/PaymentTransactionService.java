package com.abhay.payment.application.service;

import com.abhay.payment.application.exception.TransactionNotFoundException;
import com.abhay.payment.application.port.ExpiryPolicy;
import com.abhay.payment.application.port.IdempotencyStore;
import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.repository.TransactionRepository;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Application facade coordinating transaction use cases.
 *
 * This in-memory implementation is suitable for learning and single-process
 * execution. A production implementation must atomically persist transaction
 * changes and idempotency records in durable storage.
 */
public final class PaymentTransactionService {

    private final TransactionRepository transactions;
    private final IdempotencyStore idempotency;
    private final ExpiryPolicy expiryPolicy;
    private final Clock clock;

    public PaymentTransactionService(
            TransactionRepository transactions,
            IdempotencyStore idempotency,
            ExpiryPolicy expiryPolicy,
            Clock clock
    ) {
        this.transactions = Objects.requireNonNull(transactions);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.expiryPolicy = Objects.requireNonNull(expiryPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    public Transaction create(Money amount, String idempotencyKey) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "CREATE|" + amount,
                () -> {
                    Transaction transaction = Transaction.create(amount, clock);
                    transactions.save(transaction);
                    return transaction;
                }
        );
    }

    public Transaction hold(UUID transactionId, String idempotencyKey) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "HOLD|" + transactionId,
                () -> {
                    Transaction transaction = find(transactionId);
                    transaction.hold(clock);
                    transaction.setExpiry(expiryPolicy.expiryAt(transaction.updatedAt()));
                    transactions.save(transaction);
                    return transaction;
                }
        );
    }

    public Transaction capture(
            UUID transactionId,
            Money amount,
            String idempotencyKey
    ) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "CAPTURE|" + transactionId + "|" + amount,
                () -> {
                    Transaction transaction = find(transactionId);
                    transaction.capture(amount, clock);
                    transactions.save(transaction);
                    return transaction;
                }
        );
    }

    public Transaction release(
            UUID transactionId,
            Money amount,
            String idempotencyKey
    ) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "RELEASE|" + transactionId + "|" + amount,
                () -> {
                    Transaction transaction = find(transactionId);
                    transaction.release(amount, clock);
                    transactions.save(transaction);
                    return transaction;
                }
        );
    }

    public Transaction expire(UUID transactionId, String idempotencyKey) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "EXPIRE|" + transactionId,
                () -> {
                    Transaction transaction = find(transactionId);
                    transaction.expire(clock);
                    transactions.save(transaction);
                    return transaction;
                }
        );
    }

    public Transaction get(UUID transactionId) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return find(transactionId);
    }

    private Transaction find(UUID transactionId) {
        return transactions.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }
}
