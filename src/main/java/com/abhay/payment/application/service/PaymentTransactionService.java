package com.abhay.payment.application.service;

import com.abhay.payment.application.dto.TransactionSnapshot;
import com.abhay.payment.application.exception.TransactionNotFoundException;
import com.abhay.payment.application.port.ExpiryPolicy;
import com.abhay.payment.application.port.IdempotencyStore;
import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.repository.TransactionRepository;
import com.abhay.payment.domain.repository.TenantAwareTransactionRepository;

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

    public TransactionSnapshot create(Money amount, String idempotencyKey) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "CREATE|" + amount,
                () -> {
                    Transaction transaction = Transaction.create(amount, clock);
                    transactions.save(transaction);
                    return snapshot(transaction);
                }
        );
    }

    public TransactionSnapshot hold(UUID transactionId, String idempotencyKey) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "HOLD|" + transactionId,
                () -> {
                    Transaction transaction = find(transactionId);
                    synchronized (transaction) {
                        transaction.hold(clock);
                        transaction.setExpiry(expiryPolicy.expiryAt(transaction.updatedAt()));
                        transactions.save(transaction);
                        return snapshot(transaction);
                    }
                }
        );
    }

    public TransactionSnapshot capture(
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
                    synchronized (transaction) {
                        transaction.capture(amount, clock);
                        transactions.save(transaction);
                        return snapshot(transaction);
                    }
                }
        );
    }

    public TransactionSnapshot release(
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
                    synchronized (transaction) {
                        transaction.release(amount, clock);
                        transactions.save(transaction);
                        return snapshot(transaction);
                    }
                }
        );
    }

    public TransactionSnapshot expire(UUID transactionId, String idempotencyKey) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return idempotency.execute(
                idempotencyKey,
                "EXPIRE|" + transactionId,
                () -> {
                    Transaction transaction = find(transactionId);
                    synchronized (transaction) {
                        transaction.expire(clock);
                        transactions.save(transaction);
                        return snapshot(transaction);
                    }
                }
        );
    }

    public TransactionSnapshot get(UUID transactionId) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return snapshot(find(transactionId));
    }

    /**
     * Merchant-scoped API operations. Merchant identity is passed by the
     * authenticated API adapter and is never accepted from client payloads.
     */
    public TransactionSnapshot createForMerchant(String merchantId, Money amount, String key) {
        Objects.requireNonNull(amount);
        return idempotency.execute(merchantId, key, "CREATE|" + amount, () -> {
            Transaction tx = Transaction.create(amount, clock);
            tenantRepository().save(tx, merchantId);
            return snapshot(tx);
        });
    }

    public TransactionSnapshot holdForMerchant(String merchantId, UUID id, String key) {
        return idempotency.execute(merchantId, key, "HOLD|" + id, () -> {
            Transaction tx = find(id, merchantId);
            synchronized (tx) {
                tx.hold(clock);
                tx.setExpiry(expiryPolicy.expiryAt(tx.updatedAt()));
                tenantRepository().save(tx, merchantId);
                return snapshot(tx);
            }
        });
    }

    public TransactionSnapshot captureForMerchant(String merchantId, UUID id, Money amount, String key) {
        Objects.requireNonNull(amount);
        return idempotency.execute(merchantId, key, "CAPTURE|" + id + "|" + amount, () -> {
            Transaction tx = find(id, merchantId);
            synchronized (tx) {
                tx.capture(amount, clock);
                tenantRepository().save(tx, merchantId);
                return snapshot(tx);
            }
        });
    }

    public TransactionSnapshot releaseForMerchant(String merchantId, UUID id, Money amount, String key) {
        Objects.requireNonNull(amount);
        return idempotency.execute(merchantId, key, "RELEASE|" + id + "|" + amount, () -> {
            Transaction tx = find(id, merchantId);
            synchronized (tx) {
                tx.release(amount, clock);
                tenantRepository().save(tx, merchantId);
                return snapshot(tx);
            }
        });
    }

    public TransactionSnapshot expireForMerchant(String merchantId, UUID id, String key) {
        return idempotency.execute(merchantId, key, "EXPIRE|" + id, () -> {
            Transaction tx = find(id, merchantId);
            synchronized (tx) {
                tx.expire(clock);
                tenantRepository().save(tx, merchantId);
                return snapshot(tx);
            }
        });
    }

    public TransactionSnapshot getForMerchant(String merchantId, UUID id) {
        return snapshot(find(id, merchantId));
    }

    private Transaction find(UUID transactionId, String merchantId) {
        return tenantRepository().findById(transactionId, merchantId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private TenantAwareTransactionRepository tenantRepository() {
        if (transactions instanceof TenantAwareTransactionRepository tenantAware) return tenantAware;
        throw new IllegalStateException("Tenant-aware transaction persistence is not configured");
    }

    private TransactionSnapshot snapshot(Transaction transaction) {
        synchronized (transaction) {
            return new TransactionSnapshot(
                    transaction.id(),
                    transaction.originalAmount(),
                    transaction.capturedAmount(),
                    transaction.heldAmount(),
                    transaction.releasedAmount(),
                    transaction.status(),
                    transaction.createdAt(),
                    transaction.updatedAt(),
                    transaction.expiresAt(),
                    transaction.events()
            );
        }
    }

    private Transaction find(UUID transactionId) {
        return transactions.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }
}
