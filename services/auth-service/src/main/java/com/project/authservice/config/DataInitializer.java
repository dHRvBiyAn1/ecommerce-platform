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

    @Override
    @Transactional
    public void run(String... args) {
        if (roleRepository.findByName("ROLE_ADMIN").isPresent()) {
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
                .map(name -> {
                    Permission p = new Permission();
                    p.setName(name);
                    return permissionRepository.save(p);
                })
                .collect(Collectors.toList());

        Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        adminRole.setPermissions(Set.copyOf(allPermissions));
        roleRepository.save(adminRole);

        List<String> sellerPermissions = Arrays.asList(
                "products:create", "products:read", "products:update", "products:delete",
                "orders:read", "inventory:read", "inventory:write"
        );
        Role sellerRole = new Role();
        sellerRole.setName("ROLE_SELLER");
        sellerRole.setPermissions(allPermissions.stream()
                .filter(p -> sellerPermissions.contains(p.getName()))
                .collect(Collectors.toSet()));
        roleRepository.save(sellerRole);

        List<String> customerPermissions = Arrays.asList(
                "products:read", "orders:create", "orders:read", "orders:update", "orders:cancel"
        );
        Role customerRole = new Role();
        customerRole.setName("ROLE_CUSTOMER");
        customerRole.setPermissions(allPermissions.stream()
                .filter(p -> customerPermissions.contains(p.getName()))
                .collect(Collectors.toSet()));
        roleRepository.save(customerRole);

        log.info("Initialized roles: ROLE_ADMIN, ROLE_SELLER, ROLE_CUSTOMER with {} permissions", allPermissions.size());
    }
}
