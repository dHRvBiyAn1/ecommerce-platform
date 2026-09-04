package com.project.coupon.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.COUPON_RESERVATION_CONFLICT;

public class CouponReservationConflictException extends BusinessException {

    public CouponReservationConflictException(String message) {
        super(HttpStatus.CONFLICT, COUPON_RESERVATION_CONFLICT.value(), message);
    }
}
