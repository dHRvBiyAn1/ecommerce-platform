package com.project.authservice.mapper;

import com.project.authservice.dto.RegistrationRequest;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-04T21:44:57+0530",
    comments = "version: 1.6.0.Beta1, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toEntity(RegistrationRequest request) {
        if ( request == null ) {
            return null;
        }

        User user = new User();

        user.setEmail( request.getEmail() );
        user.setDisplayName( request.getDisplayName() );

        return user;
    }

    @Override
    public UserProfileDto toDto(User user) {
        if ( user == null ) {
            return null;
        }

        UserProfileDto userProfileDto = new UserProfileDto();

        userProfileDto.setRoles( mapRoles( user.getRoles() ) );
        userProfileDto.setPermissions( mapPermissions( user.getRoles() ) );
        userProfileDto.setId( user.getId() );
        userProfileDto.setEmail( user.getEmail() );
        userProfileDto.setDisplayName( user.getDisplayName() );
        userProfileDto.setImageUrl( user.getImageUrl() );
        userProfileDto.setCreatedAt( user.getCreatedAt() );

        return userProfileDto;
    }
}
