package com.abhay.payment.infrastructure.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfiguration {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService userDetailsService(PasswordEncoder encoder,
  @Value("${PAYMENT_API_USERNAME:payment-api}") String username,@Value("${PAYMENT_API_PASSWORD:}") String password) {
  if(password.isBlank()) throw new IllegalStateException("Set PAYMENT_API_PASSWORD before starting the API");
  return new InMemoryUserDetailsManager(User.withUsername(username).password(encoder.encode(password)).roles("PAYMENT_API").build());
 }
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  return http.csrf(csrf->csrf.disable()).authorizeHttpRequests(auth->auth.requestMatchers("/actuator/health","/actuator/info").permitAll().anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build();
 }
}