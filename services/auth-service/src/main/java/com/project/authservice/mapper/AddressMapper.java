package com.project.authservice.mapper;

import com.project.authservice.entity.Address;
import com.project.authservice.generated.model.AddressDto;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    implementationPackage = "com.project.authservice.generated.mapper")
public abstract class AddressMapper {
  public abstract Address toEntity(AddressDto dto);

  public AddressDto toDto(Address address) {
    if (address == null || address.isBlank()) return null;
    return mapAddress(address);
  }

  @org.mapstruct.Named("unfilteredAddress")
  protected abstract AddressDto mapAddress(Address address);
}
