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

        Optional<User> existingAdmin = userRepository.findAll()
                .stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("ADMIN")))
                .findFirst();

        if (existingAdmin.isPresent()) {
            User admin = existingAdmin.get();
            // Ensure the admin can log in — set isVerified=true if not already set
            if (!Boolean.TRUE.equals(admin.getIsVerified())) {
                admin.setIsVerified(true);
                userRepository.save(admin);
                log.warn("Fixed existing admin user: set isVerified=true for email={}", admin.getEmail());
            } else {
                log.info("Admin user already exists. Skipping bootstrap.");
            }
            return;
        }

        User admin = User.builder()
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .isActive(true)
                .isVerified(true)   // admin can log in immediately — no email verification step
                .build();

        admin.getRoles().add(adminRole);

        userRepository.save(admin);

        log.warn("🚀 Bootstrap ADMIN created!");
        log.warn("Username: admin");
        log.warn("Email: admin@example.com");
        log.warn("Password: Password123!");
        log.warn("Please change this password immediately.");
    }
}
