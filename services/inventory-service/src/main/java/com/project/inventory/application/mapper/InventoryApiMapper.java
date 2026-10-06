package com.project.inventory.application.mapper;

import com.project.inventory.generated.model.InventoryRequest;
import com.project.inventory.generated.model.PageInventoryResponse;
import com.project.inventory.generated.model.PageInventoryResponsePageable;
import com.project.inventory.generated.model.PageInventoryResponseSort;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@Mapper(componentModel = "spring", implementationPackage = "com.project.inventory.generated.mapper")
public interface InventoryApiMapper {

    @Mapping(target = "quantity", expression = "java(request.getQuantity() == null ? 0 : request.getQuantity())")
    @Mapping(target = "lowStockThreshold", expression = "java(request.getLowStockThreshold() == null ? 0 : request.getLowStockThreshold())")
    com.project.inventory.api.dto.request.InventoryRequest toDomain(InventoryRequest request);

    com.project.inventory.generated.model.InventoryResponse toApi(
            com.project.inventory.api.dto.response.InventoryResponse response);

    default PageInventoryResponse toApi(Page<com.project.inventory.api.dto.response.InventoryResponse> page) {
        PageInventoryResponsePageable pageable = new PageInventoryResponsePageable()
                .offset(page.getPageable().getOffset())
                .sort(toApi(page.getSort()))
                .paged(page.getPageable().isPaged())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .unpaged(page.getPageable().isUnpaged());
        return new PageInventoryResponse()
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .size(page.getSize())
                .content(page.getContent().stream().map(this::toApi).toList())
                .number(page.getNumber())
                .sort(toApi(page.getSort()))
                .pageable(pageable)
                .numberOfElements(page.getNumberOfElements())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty());
    }

    default PageInventoryResponseSort toApi(Sort sort) {
        return new PageInventoryResponseSort()
                .empty(sort.isEmpty())
                .sorted(sort.isSorted())
                .unsorted(sort.isUnsorted());
    }
}
