package com.abhay.payment.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public final class MerchantUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public MerchantUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return jdbc.query("""
                SELECT u.username, u.password_hash, u.merchant_id, u.merchant_role, u.enabled
                FROM merchant_user u
                JOIN merchant m ON m.id = u.merchant_id
                WHERE u.username = ? AND m.status = 'ACTIVE'
                """, rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("Invalid credentials");
            return new MerchantPrincipal(rs.getString("username"), rs.getString("password_hash"),
                    rs.getString("merchant_id"), rs.getString("merchant_role"), rs.getBoolean("enabled"));
        }, username);
    }
}
