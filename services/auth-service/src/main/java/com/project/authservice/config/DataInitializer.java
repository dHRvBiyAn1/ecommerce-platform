package com.project.authservice.config;

import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.entity.UserCredential;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import com.project.common.constant.Permissions;
import com.project.common.constant.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent on-startup data initializer. Seeds permissions, default roles, and a
 * bootstrap admin user from environment variables.
 *
 * <p>Configuration:
 * <pre>
 * admin.email=${ADMIN_EMAIL:}
 * admin.password=${ADMIN_PASSWORD:}
 * admin.display-name=${ADMIN_DISPLAY_NAME:Platform Admin}
 * </pre>
 *
 * <p>Without {@code ADMIN_EMAIL} + {@code ADMIN_PASSWORD} the bootstrap admin is
 * <strong>not</strong> created and you must seed one manually. We intentionally do
 * NOT generate a default admin password — that's the kind of thing that gets shipped
 * to production by accident.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;
    @Value("${admin.password:}")
    private String adminPassword;
    @Value("${admin.display-name:Platform Admin}")
    private String adminDisplayName;

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissions();
        Map<String, Role> roles = seedRoles();
        seedAdmin(roles.get(Roles.ADMIN));
    }

    private void seedPermissions() {
        List<String> all = List.of(
                Permissions.USERS_READ, Permissions.USERS_WRITE,
                Permissions.ROLES_READ, Permissions.ROLES_WRITE,
                Permissions.PRODUCTS_CREATE, Permissions.PRODUCTS_READ,
                Permissions.PRODUCTS_UPDATE, Permissions.PRODUCTS_DELETE,
                Permissions.ORDERS_CREATE, Permissions.ORDERS_READ,
                Permissions.ORDERS_UPDATE, Permissions.ORDERS_CANCEL,
                Permissions.ORDERS_REFUND,
                Permissions.INVENTORY_READ, Permissions.INVENTORY_WRITE,
                Permissions.INVENTORY_RESERVE,
                Permissions.PAYMENTS_READ, Permissions.PAYMENTS_REFUND,
                Permissions.PAYMENTS_PROCESS,
                Permissions.NOTIFICATIONS_READ, Permissions.NOTIFICATIONS_SEND,
                Permissions.COUPONS_READ, Permissions.COUPONS_WRITE,
                Permissions.CMS_WRITE, Permissions.ANALYTICS_READ, Permissions.AUDIT_READ
        );
        for (String p : all) {
            if (permissionRepository.findByName(p).isEmpty()) {
                Permission perm = new Permission();
                perm.setName(p);
                permissionRepository.save(perm);
            }
        }
    }

    private Map<String, Role> seedRoles() {
        Map<String, Role> result = new LinkedHashMap<>();
        result.put(Roles.ADMIN, ensureRole(Roles.ADMIN, allPermissions()));
        result.put(Roles.SELLER, ensureRole(Roles.SELLER, sellerPermissions()));
        result.put(Roles.CUSTOMER, ensureRole(Roles.CUSTOMER, customerPermissions()));
        result.put(Roles.SUPPORT, ensureRole(Roles.SUPPORT, supportPermissions()));
        return result;
    }

    private Role ensureRole(String name, Set<Permission> permissions) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setPermissions(permissions);
            log.info("Seeded role {}", name);
            return roleRepository.save(r);
        });
    }

    private Set<Permission> allPermissions() {
        return new HashSet<>(permissionRepository.findAll());
    }

    private Set<Permission> sellerPermissions() {
        return permsByName(Set.of(
                Permissions.PRODUCTS_CREATE, Permissions.PRODUCTS_READ,
                Permissions.PRODUCTS_UPDATE, Permissions.PRODUCTS_DELETE,
                Permissions.ORDERS_READ,
                Permissions.INVENTORY_READ, Permissions.INVENTORY_WRITE,
                Permissions.PAYMENTS_READ));
    }

    private Set<Permission> customerPermissions() {
        return permsByName(Set.of(
                Permissions.PRODUCTS_READ,
                Permissions.ORDERS_CREATE, Permissions.ORDERS_READ,
                Permissions.ORDERS_CANCEL,
                Permissions.PAYMENTS_READ));
    }

    private Set<Permission> supportPermissions() {
        return permsByName(Set.of(
                Permissions.USERS_READ,
                Permissions.ORDERS_READ, Permissions.ORDERS_REFUND,
                Permissions.PAYMENTS_READ, Permissions.PAYMENTS_REFUND,
                Permissions.NOTIFICATIONS_READ, Permissions.AUDIT_READ));
    }

    private Set<Permission> permsByName(Set<String> names) {
        Set<Permission> out = new HashSet<>();
        for (String n : names) {
            permissionRepository.findByName(n).ifPresent(out::add);
        }
        return out;
    }

    private void seedAdmin(Role adminRole) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD not set; skipping bootstrap admin creation");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Bootstrap admin {} already exists", adminEmail);
            return;
        }
        User user = new User();
        user.setEmail(adminEmail);
        user.setDisplayName(adminDisplayName);
        user.setActive(true);
        user.getRoles().add(adminRole);
        user = userRepository.save(user);

        UserCredential cred = new UserCredential();
        cred.setUser(user);
        cred.setAuthProvider(AuthProvider.LOCAL);
        cred.setPasswordHash(passwordEncoder.encode(adminPassword));
        userCredentialRepository.save(cred);

        log.info("Bootstrap admin created: {}", adminEmail);
    }
}
