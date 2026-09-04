package com.project.coupon.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.INVALID_COUPON_REQUEST;

public class InvalidCouponRequestException extends BusinessException {

    public InvalidCouponRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, INVALID_COUPON_REQUEST.value(), message);
    }
}
