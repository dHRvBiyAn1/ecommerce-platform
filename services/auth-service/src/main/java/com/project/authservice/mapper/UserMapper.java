package com.project.authservice.mapper;

import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hand-rolled bean that maps the JPA {@link User} entity onto the public
 * {@link UserProfileDto}. Replaces the previous MapStruct-generated
 * {@code @Mapper(componentModel="spring")} variant — MapStruct's generated
 * {@code UserMapperImpl} wasn't being picked up consistently by Spring's
 * component scan in our build. A plain {@code @Component} is more reliable
 * and avoids the annotation-processor dance.
 */
@Component
@RequiredArgsConstructor
public class UserMapper {

    private final UserCredentialRepository userCredentialRepository;

    public UserProfileDto toDto(User user) {
        if (user == null) return null;

        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setImageUrl(user.getImageUrl());
        dto.setPhone(user.getPhone());
        dto.setActive(user.isActive());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(mapRoleNames(user.getRoles()));
        dto.setPermissions(mapPermissionNames(user.getRoles()));
        dto.setShippingAddress(toAddressDto(user.getShippingAddress()));
        dto.setBillingAddress(toAddressDto(user.getBillingAddress()));
        // hasPassword is true if the user has any LOCAL credential row.
        // OAuth2-only users have no LOCAL credential and therefore no password
        // to change, so the frontend hides the change-password card for them.
        dto.setHasPassword(userCredentialRepository
                .findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL)
                .isPresent());
        return dto;
    }

    private com.project.authservice.dto.AddressDto toAddressDto(com.project.authservice.entity.Address a) {
        if (a == null || a.isBlank()) return null;
        com.project.authservice.dto.AddressDto dto = new com.project.authservice.dto.AddressDto();
        dto.setFullName(a.getFullName());
        dto.setPhone(a.getPhone());
        dto.setStreet(a.getStreet());
        dto.setCity(a.getCity());
        dto.setState(a.getState());
        dto.setZipCode(a.getZipCode());
        dto.setCountry(a.getCountry());
        return dto;
    }

    private Set<String> mapRoleNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    private Set<String> mapPermissionNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }
}
