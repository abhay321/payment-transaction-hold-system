package com.abhay.payment.infrastructure.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationService {
    public record SettlementInput(UUID transactionId, String providerReference,
            BigDecimal settledAmount, String currency, String providerStatus, Instant settledAt) { }
    private final JdbcTemplate jdbc;
    public ReconciliationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> ingest(String merchantId, SettlementInput input) {
        if (input.transactionId() == null || input.providerReference() == null
                || input.providerReference().isBlank() || input.providerReference().length() > 160
                || input.settledAmount() == null || input.settledAmount().signum() <= 0
                || input.currency() == null || !input.currency().matches("[A-Z]{3}")
                || !"CAPTURED".equals(input.providerStatus()) || input.settledAt() == null)
            throw new IllegalArgumentException("A valid CAPTURED settlement record is required");

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT status, captured_amount, currency FROM payment_transaction
            WHERE id=? AND merchant_id=?
            """, input.transactionId(), merchantId);
        String result = "MATCHED";
        String reason = null;
        if (rows.isEmpty()) {
            result = "UNMATCHED";
            reason = "No transaction exists for this merchant and transaction ID";
        } else {
            Map<String, Object> tx = rows.getFirst();
            BigDecimal captured = (BigDecimal) tx.get("captured_amount");
            String currency = (String) tx.get("currency");
            String status = (String) tx.get("status");
            if (!(status.equals("CAPTURED") || status.equals("PARTIALLY_CAPTURED"))) {
                result = "MISMATCH";
                reason = "Provider reports CAPTURED but internal transaction status is " + status;
            } else if (!currency.equals(input.currency()) || captured.compareTo(input.settledAmount()) != 0) {
                result = "MISMATCH";
                reason = "Captured amount or currency differs from provider settlement";
            }
        }
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
            INSERT INTO provider_settlement_record(id, merchant_id, transaction_id,
                provider_reference, settled_amount, currency, provider_status, settled_at,
                reconciliation_status, exception_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (merchant_id, provider_reference) DO NOTHING
            """, id, merchantId, input.transactionId(), input.providerReference(),
                input.settledAmount(), input.currency(), input.providerStatus(),
                Timestamp.from(input.settledAt()), result, reason);
        Map<String, Object> persisted = jdbc.queryForMap("""
            SELECT * FROM provider_settlement_record
            WHERE merchant_id=? AND provider_reference=?
            """, merchantId, input.providerReference());
        if (inserted == 0 && (!input.transactionId().equals(persisted.get("transaction_id"))
                || ((BigDecimal) persisted.get("settled_amount")).compareTo(input.settledAmount()) != 0
                || !input.currency().equals(persisted.get("currency"))
                || !input.providerStatus().equals(persisted.get("provider_status")))) {
            throw new IllegalArgumentException("Provider reference was already used for different settlement data");
        }
        return persisted;
    }

    public List<Map<String, Object>> exceptions(String merchantId) {
        return jdbc.queryForList("""
            SELECT id, transaction_id, provider_reference, settled_amount, currency,
                   provider_status, settled_at, reconciliation_status, exception_reason, created_at
            FROM provider_settlement_record
            WHERE merchant_id=? AND reconciliation_status <> 'MATCHED'
            ORDER BY created_at DESC LIMIT 200
            """, merchantId);
    }
}
