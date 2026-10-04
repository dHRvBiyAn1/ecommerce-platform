package com.project.authservice.mapper;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public Address toEntity(AddressDto dto) {
        if (dto == null) return null;
        return Address.builder()
                .fullName(dto.fullName())
                .phone(dto.phone())
                .street(dto.street())
                .city(dto.city())
                .state(dto.state())
                .zipCode(dto.zipCode())
                .country(dto.country())
                .build();
    }

    public AddressDto toDto(Address address) {
        if (address == null || address.isBlank()) return null;
        return new AddressDto(address.getFullName(), address.getPhone(), address.getStreet(),
                address.getCity(), address.getState(), address.getZipCode(), address.getCountry());
    }
}
