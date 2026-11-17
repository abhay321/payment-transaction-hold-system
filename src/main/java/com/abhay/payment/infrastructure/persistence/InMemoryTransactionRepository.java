package com.abhay.payment.infrastructure.persistence;

import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.repository.TransactionRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local repository for the learning implementation.
 * The aggregate itself serializes its own mutations.
 */
public final class InMemoryTransactionRepository implements TransactionRepository {

    private final ConcurrentMap<UUID, Transaction> transactions =
            new ConcurrentHashMap<>();

    @Override
    public void save(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null");
        transactions.put(transaction.id(), transaction);
    }

    @Override
    public Optional<Transaction> findById(UUID transactionId) {
        Objects.requireNonNull(transactionId, "Transaction id cannot be null");
        return Optional.ofNullable(transactions.get(transactionId));
    }
}
