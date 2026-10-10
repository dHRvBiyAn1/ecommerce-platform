package com.project.authservice.mapper;

import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.SellerApplicationResponse;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    uses = AddressMapper.class,
    injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR,
    implementationPackage = "com.project.authservice.generated.mapper")
public abstract class SellerApplicationMapper {
  @org.mapstruct.Mapping(target = "id", source = "application.id")
  @org.mapstruct.Mapping(target = "userId", source = "application.userId")
  @org.mapstruct.Mapping(target = "userEmail", source = "user.email")
  @org.mapstruct.Mapping(target = "userDisplayName", source = "user.displayName")
  public abstract SellerApplicationResponse toResponse(SellerApplication application, User user);
}
