package com.project.coupon.mapper;

import com.project.coupon.generated.model.PageCouponResponse;
import com.project.coupon.generated.model.PageCouponResponsePageable;
import com.project.coupon.generated.model.PageCouponResponseSort;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@Mapper(componentModel = "spring", implementationPackage = "com.project.coupon.generated.mapper")
public interface CouponApiMapper {

  default PageCouponResponse toApi(Page<com.project.coupon.generated.model.CouponResponse> page) {
    PageCouponResponsePageable pageable =
        new PageCouponResponsePageable()
            .offset(page.getPageable().getOffset())
            .sort(toApi(page.getSort()))
            .paged(page.getPageable().isPaged())
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .unpaged(page.getPageable().isUnpaged());
    return new PageCouponResponse()
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .size(page.getSize())
        .content(page.getContent())
        .number(page.getNumber())
        .sort(toApi(page.getSort()))
        .pageable(pageable)
        .numberOfElements(page.getNumberOfElements())
        .first(page.isFirst())
        .last(page.isLast())
        .empty(page.isEmpty());
  }

  default PageCouponResponseSort toApi(Sort sort) {
    return new PageCouponResponseSort()
        .empty(sort.isEmpty())
        .sorted(sort.isSorted())
        .unsorted(sort.isUnsorted());
  }
}
