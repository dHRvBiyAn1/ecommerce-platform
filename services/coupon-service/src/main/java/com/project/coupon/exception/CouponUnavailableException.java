package com.project.coupon.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.COUPON_UNAVAILABLE;

public class CouponUnavailableException extends BusinessException {

    public CouponUnavailableException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, COUPON_UNAVAILABLE.value(), message);
    }
}
