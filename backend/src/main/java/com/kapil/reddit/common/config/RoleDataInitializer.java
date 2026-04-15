package com.kapil.reddit.common.config;

import com.kapil.reddit.user.domain.Permission;
import com.kapil.reddit.user.domain.Role;
import com.kapil.reddit.user.repository.PermissionRepository;
import com.kapil.reddit.user.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleDataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @PostConstruct
    public void init() {
        initRoles();
        initPermissions();
        assignPermissionsToRoles();
    }

    private void initRoles() {
        List<String> roles = List.of(
                "USER",
                "COMMUNITY_MODERATOR",
                "GLOBAL_MODERATOR",
                "ADMIN"
        );

        for (String roleName : roles) {
            roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(
                            Role.builder()
                                    .name(roleName)
                                    .build()
                    ));
        }
    }

    private void initPermissions() {

        List<String> permissions = List.of(
                "USER_READ",
                "USER_WRITE",
                "POST_CREATE",
                "POST_DELETE",
                "POST_UPDATE",
                "COMMENT_CREATE",
                "COMMENT_DELETE",
                "USER_PROMOTE"
        );

        for (String permissionName : permissions) {
            permissionRepository.findByName(permissionName)
                    .orElseGet(() -> permissionRepository.save(
                            Permission.builder()
                                    .name(permissionName)
                                    .build()
                    ));
        }
    }

    private void assignPermissionsToRoles() {

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        Permission postCreate = permissionRepository.findByName("POST_CREATE").orElseThrow();
        Permission commentCreate = permissionRepository.findByName("COMMENT_CREATE").orElseThrow();
        Permission userRead = permissionRepository.findByName("USER_READ").orElseThrow();

        // USER permissions
        userRole.getPermissions().add(postCreate);
        userRole.getPermissions().add(commentCreate);
        userRole.getPermissions().add(userRead);

        // ADMIN gets everything
        adminRole.getPermissions().addAll(permissionRepository.findAll());

        roleRepository.save(userRole);
        roleRepository.save(adminRole);
    }

}
