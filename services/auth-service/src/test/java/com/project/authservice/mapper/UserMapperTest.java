package com.project.authservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.UserProfileDto;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserMapperTest {

  private final UserMapper mapper =
      new com.project.authservice.generated.mapper.UserMapperImpl(
          new com.project.authservice.generated.mapper.AddressMapperImpl());

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

    assertThat(profile.getHasPassword()).isTrue();
    assertThat(profile.getRoles()).containsExactly("ROLE_CUSTOMER");
    assertThat(profile.getPermissions()).containsExactly("users:read");
    assertThatThrownBy(() -> profile.getRoles().add("ROLE_ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> profile.getPermissions().add("users:write"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
