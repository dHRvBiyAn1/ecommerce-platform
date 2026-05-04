package com.project.authservice.mapper;

import com.project.authservice.dto.RegistrationRequest;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(RegistrationRequest request);

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRoles")
    @Mapping(target = "permissions", source = "roles", qualifiedByName = "mapPermissions")
    UserProfileDto toDto(User user);

    @Named("mapRoles")
    default Set<String> mapRoles(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    @Named("mapPermissions")
    default Set<String> mapPermissions(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }
}
