package com.project.coupon.mapper;

import com.project.coupon.generated.model.PageCouponResponse;
import com.project.coupon.generated.model.PageCouponResponsePageable;
import com.project.coupon.generated.model.PageCouponResponseSort;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@Mapper(componentModel = "spring", implementationPackage = "com.project.coupon.generated.mapper")
public interface CouponApiMapper {

    com.project.coupon.dto.CouponRequest toDomain(com.project.coupon.generated.model.CouponRequest request);

    com.project.coupon.dto.ValidateCouponRequest toDomain(
            com.project.coupon.generated.model.ValidateCouponRequest request);

    com.project.coupon.dto.CouponReservationRequest toDomain(
            com.project.coupon.generated.model.CouponReservationRequest request);

    com.project.coupon.dto.CouponTransitionRequest toDomain(
            com.project.coupon.generated.model.CouponTransitionRequest request);

    com.project.coupon.dto.RedeemCouponRequest toDomain(
            com.project.coupon.generated.model.RedeemCouponRequest request);

    com.project.coupon.generated.model.CouponResponse toApi(com.project.coupon.dto.CouponResponse response);

    com.project.coupon.generated.model.CouponReservationResponse toApi(
            com.project.coupon.dto.CouponReservationResponse response);

    com.project.coupon.generated.model.ValidateCouponResponse toApi(
            com.project.coupon.dto.ValidateCouponResponse response);

    default PageCouponResponse toApi(Page<com.project.coupon.dto.CouponResponse> page) {
        PageCouponResponsePageable pageable = new PageCouponResponsePageable()
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
                .content(page.getContent().stream().map(this::toApi).toList())
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
