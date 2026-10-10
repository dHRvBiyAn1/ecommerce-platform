package com.project.authservice.mapper;

import com.project.authservice.entity.Address;
import com.project.authservice.generated.model.AddressDto;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {
    public Address toEntity(AddressDto dto) {
        if (dto == null) return null;
        return Address.builder().fullName(dto.getFullName()).phone(dto.getPhone()).street(dto.getStreet())
                .city(dto.getCity()).state(dto.getState()).zipCode(dto.getZipCode()).country(dto.getCountry()).build();
    }

    public AddressDto toDto(Address address) {
        if (address == null || address.isBlank()) return null;
        return new AddressDto().fullName(address.getFullName()).phone(address.getPhone()).street(address.getStreet())
                .city(address.getCity()).state(address.getState()).zipCode(address.getZipCode())
                .country(address.getCountry());
    }
}
