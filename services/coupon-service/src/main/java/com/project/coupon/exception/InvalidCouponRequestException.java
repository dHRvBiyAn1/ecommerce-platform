package com.project.coupon.exception;

import com.project.common.exception.ValidationException;

public class InvalidCouponRequestException extends ValidationException {

    public InvalidCouponRequestException(String message) {
        super(message);
    }
}
