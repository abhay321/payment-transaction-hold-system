package com.abhay.payment.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated identity carrying the server-verified merchant boundary. */
public final class MerchantPrincipal implements UserDetails {
    private final String username;
    private final String password;
    private final String merchantId;
    private final String role;
    private final boolean enabled;

    public MerchantPrincipal(String username, String password, String merchantId,
                             String role, boolean enabled) {
        this.username = username;
        this.password = password;
        this.merchantId = merchantId;
        this.role = role;
        this.enabled = enabled;
    }

    public String merchantId() { return merchantId; }
    public String role() { return role; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
