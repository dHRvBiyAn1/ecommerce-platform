package com.project.authservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.authservice.entity.Address;
import com.project.authservice.generated.model.AddressDto;
import org.junit.jupiter.api.Test;

class AddressMapperTest {

  private final AddressMapper mapper = new AddressMapper();

  @Test
  void mapsBothDirectionsWithoutDependencies() {
    AddressDto dto =
        new AddressDto(
            "Customer One", "+1 555 0100", "1 Main Street", "Pune", "MH", "411001", "IN");

    Address entity = mapper.toEntity(dto);
    AddressDto mapped = mapper.toDto(entity);

    assertThat(mapped).isEqualTo(dto);
  }
}
