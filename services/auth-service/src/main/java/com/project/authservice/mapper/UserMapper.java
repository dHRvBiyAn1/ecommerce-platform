package com.project.authservice.mapper;

import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final AddressMapper addressMapper;

    public UserProfileDto toDto(User user, boolean hasPassword) {
        if (user == null) return null;
        return new UserProfileDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getImageUrl(),
                user.getPhone(), user.isActive(), user.getCreatedAt(), mapRoleNames(user.getRoles()),
                mapPermissionNames(user.getRoles()), addressMapper.toDto(user.getShippingAddress()),
                addressMapper.toDto(user.getBillingAddress()), hasPassword);
    }

    private Set<String> mapRoleNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return Set.copyOf(roles.stream().map(Role::getName).collect(Collectors.toSet()));
    }

    private Set<String> mapPermissionNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return Set.copyOf(roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet()));
    }
}
