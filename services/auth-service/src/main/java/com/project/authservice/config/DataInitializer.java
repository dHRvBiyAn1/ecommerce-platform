package com.project.authservice.config;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    private static final Set<String> DEFAULT_ROLE_NAMES = Set.of("ROLE_ADMIN", "ROLE_SELLER", "ROLE_CUSTOMER");

    @Override
    @Transactional
    public void run(String... args) {
        if (DEFAULT_ROLE_NAMES.stream().allMatch(name -> roleRepository.findByName(name).isPresent())) {
            log.info("Roles already initialized, skipping");
            return;
        }

        List<String> allPermissionNames = Arrays.asList(
                "admin:users:read", "admin:users:write",
                "admin:roles:read", "admin:roles:write",
                "products:create", "products:read", "products:update", "products:delete",
                "orders:create", "orders:read", "orders:update", "orders:cancel",
                "inventory:read", "inventory:write",
                "payments:read", "payments:refund",
                "notifications:read", "notifications:send"
        );

        List<Permission> allPermissions = allPermissionNames.stream()
                .filter(name -> permissionRepository.findByName(name).isEmpty())
                .map(name -> {
                    Permission p = new Permission();
                    p.setName(name);
                    return permissionRepository.save(p);
                })
                .collect(Collectors.toList());

        createRoleIfNotExists("ROLE_ADMIN", allPermissions, allPermissionNames);

        List<String> sellerPermissions = Arrays.asList(
                "products:create", "products:read", "products:update", "products:delete",
                "orders:read", "inventory:read", "inventory:write"
        );
        createRoleIfNotExists("ROLE_SELLER", allPermissions.stream()
                .filter(p -> sellerPermissions.contains(p.getName()))
                .collect(Collectors.toList()), allPermissionNames);

        List<String> customerPermissions = Arrays.asList(
                "products:read", "orders:create", "orders:read", "orders:update", "orders:cancel"
        );
        createRoleIfNotExists("ROLE_CUSTOMER", allPermissions.stream()
                .filter(p -> customerPermissions.contains(p.getName()))
                .collect(Collectors.toList()), allPermissionNames);

        log.info("Initialized roles: ROLE_ADMIN, ROLE_SELLER, ROLE_CUSTOMER with {} permissions", allPermissions.size());
    }

    private void createRoleIfNotExists(String name, List<Permission> permissions, List<String> allPermissionNames) {
        if (roleRepository.findByName(name).isPresent()) {
            log.info("Role {} already exists, skipping", name);
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setPermissions(Set.copyOf(permissions));
        roleRepository.save(role);
        log.info("Created role: {}", name);
    }
}
