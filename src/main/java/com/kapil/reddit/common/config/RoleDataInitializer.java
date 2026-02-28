package com.kapil.reddit.common.config;

import com.kapil.reddit.user.domain.Role;
import com.kapil.reddit.user.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleDataInitializer {

    private final RoleRepository roleRepository;
    @PostConstruct
    public void initRoles() {
        List<String> roles = List.of(
                "USER",
                "COMMUNITY_MODERATOR",
                "GLOBAL_MODERATOR",
                "ADMIN"
        );

        for (String roleName : roles) {
            roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(new Role(null, roleName)));
        }
    }
}
