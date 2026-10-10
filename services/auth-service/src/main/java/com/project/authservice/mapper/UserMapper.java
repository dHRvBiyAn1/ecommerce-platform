package com.project.authservice.mapper;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.UserProfileDto;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    uses = AddressMapper.class,
    injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR,
    implementationPackage = "com.project.authservice.generated.mapper")
public abstract class UserMapper {
  @org.mapstruct.Mapping(target = "roles", source = "user.roles", qualifiedByName = "roleNames")
  @org.mapstruct.Mapping(
      target = "permissions",
      source = "user.roles",
      qualifiedByName = "permissionNames")
  public abstract UserProfileDto toDto(User user, boolean hasPassword);

  @org.mapstruct.Named("roleNames")
  protected Set<String> mapRoleNames(Set<Role> roles) {
    if (roles == null) return Set.of();
    return Set.copyOf(roles.stream().map(Role::getName).collect(Collectors.toSet()));
  }

  @org.mapstruct.Named("permissionNames")
  protected Set<String> mapPermissionNames(Set<Role> roles) {
    if (roles == null) return Set.of();
    return Set.copyOf(
        roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::getName)
            .collect(Collectors.toSet()));
  }
}
