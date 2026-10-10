package com.project.authservice.mapper;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.UserProfileDto;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {
  private final AddressMapper addressMapper;

  public UserProfileDto toDto(User user, boolean hasPassword) {
    if (user == null) return null;
    return new UserProfileDto()
        .id(user.getId())
        .email(user.getEmail())
        .displayName(user.getDisplayName())
        .imageUrl(user.getImageUrl())
        .phone(user.getPhone())
        .active(user.isActive())
        .createdAt(user.getCreatedAt())
        .roles(mapRoleNames(user.getRoles()))
        .permissions(mapPermissionNames(user.getRoles()))
        .shippingAddress(addressMapper.toDto(user.getShippingAddress()))
        .billingAddress(addressMapper.toDto(user.getBillingAddress()))
        .hasPassword(hasPassword);
  }

  private Set<String> mapRoleNames(Set<Role> roles) {
    if (roles == null) return Set.of();
    return Set.copyOf(roles.stream().map(Role::getName).collect(Collectors.toSet()));
  }

  private Set<String> mapPermissionNames(Set<Role> roles) {
    if (roles == null) return Set.of();
    return Set.copyOf(
        roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::getName)
            .collect(Collectors.toSet()));
  }
}
