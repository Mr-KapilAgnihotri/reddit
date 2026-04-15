package com.kapil.reddit.common.config;

import com.kapil.reddit.user.domain.Role;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.RoleRepository;
import com.kapil.reddit.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminDataInitializer {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void initAdmin() {

        Optional<Role> adminRoleOpt = roleRepository.findByName("ADMIN");

        if (adminRoleOpt.isEmpty()) {
            log.error("ADMIN role not found. Skipping admin bootstrap.");
            return;
        }

        Role adminRole = adminRoleOpt.get();

        boolean adminExists = userRepository.findAll()
                .stream()
                .anyMatch(user ->
                        user.getRoles().stream()
                                .anyMatch(role -> role.getName().equals("ADMIN"))
                );

        if (adminExists) {
            log.info("Admin user already exists. Skipping bootstrap.");
            return;
        }

        User admin = User.builder()
                .username("admin")
                .email("admin@reddit.com")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .isActive(true)
                .build();

        admin.getRoles().add(adminRole);

        userRepository.save(admin);

        log.warn("🚀 Bootstrap ADMIN created!");
        log.warn("Username: admin");
        log.warn("Email: admin@reddit.com");
        log.warn("Password: Admin@123");
        log.warn("Please change this password immediately.");
    }
}
