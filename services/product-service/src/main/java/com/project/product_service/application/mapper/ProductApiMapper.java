package com.project.product_service.application.mapper;

import com.project.product_service.generated.model.PageProductResponse;
import com.project.product_service.generated.model.PageableObject;
import com.project.product_service.generated.model.ProductResponse;
import com.project.product_service.generated.model.SortObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public final class ProductApiMapper {

  private ProductApiMapper() {}

  public static PageProductResponse toApi(Page<ProductResponse> page) {
    var pageable = page.getPageable();
    SortObject sort = toApi(page.getSort());
    PageableObject pageableObject =
        new PageableObject()
            .offset(pageable.getOffset())
            .sort(sort)
            .paged(pageable.isPaged())
            .pageNumber(pageable.getPageNumber())
            .pageSize(pageable.getPageSize())
            .unpaged(pageable.isUnpaged());
    return new PageProductResponse()
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .size(page.getSize())
        .content(page.getContent())
        .number(page.getNumber())
        .sort(sort)
        .pageable(pageableObject)
        .numberOfElements(page.getNumberOfElements())
        .first(page.isFirst())
        .last(page.isLast())
        .empty(page.isEmpty());
  }

  private static SortObject toApi(Sort sort) {
    return new SortObject()
        .empty(sort.isEmpty())
        .sorted(sort.isSorted())
        .unsorted(sort.isUnsorted());
  }
}
