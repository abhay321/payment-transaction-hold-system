package com.abhay.payment.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Emits security-relevant audit records without credentials or payment payloads. */
@Component
public final class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    public void transactionAction(String action, MerchantPrincipal principal, UUID transactionId) {
        log.atInfo()
                .addKeyValue("securityEvent", "merchant_transaction_access")
                .addKeyValue("outcome", "SUCCESS")
                .addKeyValue("merchantId", principal.merchantId())
                .addKeyValue("subject", principal.getUsername())
                .addKeyValue("action", action)
                .addKeyValue("transactionId", transactionId)
                .addKeyValue("correlationId", MDC.get("correlationId"))
                .log("Merchant transaction action");
    }

    @EventListener
    public void authenticationSucceeded(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof MerchantPrincipal principal) {
            log.atInfo()
                    .addKeyValue("securityEvent", "merchant_authentication")
                    .addKeyValue("outcome", "SUCCESS")
                    .addKeyValue("merchantId", principal.merchantId())
                    .addKeyValue("subject", principal.getUsername())
                    .log("Merchant authentication succeeded");
        }
    }

    @EventListener
    public void authenticationFailed(AbstractAuthenticationFailureEvent event) {
        log.atWarn()
                .addKeyValue("securityEvent", "merchant_authentication")
                .addKeyValue("outcome", "FAILURE")
                .addKeyValue("subject", event.getAuthentication().getName())
                .addKeyValue("failureType", event.getException().getClass().getSimpleName())
                .log("Merchant authentication failed");
    }
}
