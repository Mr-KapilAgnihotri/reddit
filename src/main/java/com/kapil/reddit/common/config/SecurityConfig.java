package com.kapil.reddit.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())  // Disable CSRF for now
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()  // Allow all endpoints
                )
                .formLogin(form -> form.disable())  // Disable login form
                .httpBasic(basic -> basic.disable()); // Disable basic auth

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                16,
                32,
                1,
                65536,
                3
        );
    }
}