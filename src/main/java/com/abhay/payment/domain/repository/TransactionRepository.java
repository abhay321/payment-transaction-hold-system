package com.abhay.payment.domain.repository;

import com.abhay.payment.domain.model.Transaction;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for transaction aggregates. */
public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(UUID transactionId);
}
