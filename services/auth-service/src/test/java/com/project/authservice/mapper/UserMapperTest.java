package com.project.authservice.mapper;

import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper(new AddressMapper());

    @Test
    void mapsResolvedPasswordStateAndDefensivelyImmutableAuthorities() {
        Permission permission = new Permission();
        permission.setName("users:read");
        Role role = new Role();
        role.setName("ROLE_CUSTOMER");
        role.setPermissions(new HashSet<>(Set.of(permission)));
        User user = new User();
        user.setRoles(new HashSet<>(Set.of(role)));

        UserProfileDto profile = mapper.toDto(user, true);

        assertThat(profile.hasPassword()).isTrue();
        assertThat(profile.roles()).containsExactly("ROLE_CUSTOMER");
        assertThat(profile.permissions()).containsExactly("users:read");
        assertThatThrownBy(() -> profile.roles().add("ROLE_ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.permissions().add("users:write"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
