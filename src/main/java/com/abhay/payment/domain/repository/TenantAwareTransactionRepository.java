package com.abhay.payment.domain.repository;

import com.abhay.payment.domain.model.Transaction;

import java.util.Optional;
import java.util.UUID;

/**
 * Explicit tenant boundary for application operations. Callers must supply the
 * merchant ID derived from the authenticated principal, never from request data.
 */
public interface TenantAwareTransactionRepository extends TransactionRepository {
    void save(Transaction transaction, String merchantId);
    Optional<Transaction> findById(UUID transactionId, String merchantId);
}
