package com.project.authservice.config;

import com.project.authservice.entity.Role;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks private DataInitializer dataInitializer;

    @Captor private ArgumentCaptor<Role> roleCaptor;

    @Test
    void run_WhenRolesNotInitialized_CreatesAllRoles() throws Exception {
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.empty());
        when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        dataInitializer.run();

        verify(roleRepository, times(3)).save(roleCaptor.capture());
        var savedRoles = roleCaptor.getAllValues();

        assertThat(savedRoles).extracting(Role::getName)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SELLER", "ROLE_CUSTOMER");

        Role admin = savedRoles.stream().filter(r -> r.getName().equals("ROLE_ADMIN")).findFirst().orElseThrow();
        assertThat(admin.getPermissions()).hasSizeGreaterThan(10);

        Role seller = savedRoles.stream().filter(r -> r.getName().equals("ROLE_SELLER")).findFirst().orElseThrow();
        assertThat(seller.getPermissions()).extracting(p -> p.getName())
                .contains("products:create", "products:update", "products:delete", "products:read");

        Role customer = savedRoles.stream().filter(r -> r.getName().equals("ROLE_CUSTOMER")).findFirst().orElseThrow();
        assertThat(customer.getPermissions()).extracting(p -> p.getName())
                .contains("products:read", "orders:create", "orders:read");
    }

    @Test
    void run_WhenAlreadyInitialized_Skips() throws Exception {
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(new Role()));

        dataInitializer.run();

        verify(roleRepository, never()).save(any());
    }
}
