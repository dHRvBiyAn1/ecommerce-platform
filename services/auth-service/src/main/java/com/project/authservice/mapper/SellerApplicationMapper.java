package com.project.authservice.mapper;

import com.project.authservice.dto.response.seller.SellerApplicationResponse;
import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AddressMapper.class)
public interface SellerApplicationMapper {

    @Mapping(target = "id", source = "application.id")
    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "userDisplayName", source = "user.displayName")
    SellerApplicationResponse toResponse(SellerApplication application, User user);
}
