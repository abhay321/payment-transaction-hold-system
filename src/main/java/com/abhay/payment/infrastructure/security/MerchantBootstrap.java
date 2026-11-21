package com.abhay.payment.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Local/demo bootstrap only. Configure semicolon-separated entries:
 * merchant-id|username|password|MERCHANT_ADMIN (repeat for each principal).
 * Passwords are hashed before storage and are never logged.
 */
@Component
public final class MerchantBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String configuredUsers;
    private final String legacyUsername;
    private final String legacyPassword;

    public MerchantBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${PAYMENT_MERCHANT_USERS:}") String configuredUsers,
            @Value("${PAYMENT_API_USERNAME:payment-api}") String legacyUsername,
            @Value("${PAYMENT_API_PASSWORD:}") String legacyPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.configuredUsers = configuredUsers;
        this.legacyUsername = legacyUsername;
        this.legacyPassword = legacyPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        String source = configuredUsers.isBlank()
                ? "default|" + legacyUsername + "|" + legacyPassword + "|MERCHANT_ADMIN"
                : configuredUsers;
        if (source.contains("||") || source.endsWith("|")) {
            throw new IllegalStateException("Configure PAYMENT_MERCHANT_USERS with non-empty credentials");
        }

        Arrays.stream(source.split(";")).map(String::trim).filter(s -> !s.isEmpty()).forEach(entry -> {
            String[] fields = entry.split("\\|", -1);
            if (fields.length != 4 || !fields[0].matches("[A-Za-z0-9_-]{1,64}")
                    || fields[1].isBlank() || fields[1].length() > 100
                    || fields[2].length() < 16
                    || !fields[3].matches("MERCHANT_ADMIN|MERCHANT_OPERATOR|MERCHANT_READ_ONLY")) {
                throw new IllegalStateException("Invalid PAYMENT_MERCHANT_USERS entry; expected merchantId|username|password|ROLE and a password of at least 16 characters");
            }
            jdbc.update("""
                    INSERT INTO merchant(id, display_name) VALUES (?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, fields[0], fields[0]);
            jdbc.update("""
                    INSERT INTO merchant_user(username, merchant_id, password_hash, role, enabled)
                    VALUES (?, ?, ?, ?, TRUE)
                    ON CONFLICT (username) DO UPDATE SET merchant_id=EXCLUDED.merchant_id,
                        password_hash=EXCLUDED.password_hash, role=EXCLUDED.role, enabled=TRUE
                    """, fields[1], fields[0], encoder.encode(fields[2]), fields[3]);
        });
    }
}
