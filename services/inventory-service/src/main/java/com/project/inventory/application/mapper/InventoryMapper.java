package com.project.inventory.application.mapper;

import com.project.inventory.domain.model.InventoryItem;
import com.project.inventory.generated.model.InventoryResponse;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    implementationPackage = "com.project.inventory.generated.mapper",
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface InventoryMapper {
  @org.mapstruct.Mapping(
      target = "availableQuantity",
      expression = "java(item.getQuantity() - item.getReservedQuantity())")
  InventoryResponse toResponse(InventoryItem item);
}
