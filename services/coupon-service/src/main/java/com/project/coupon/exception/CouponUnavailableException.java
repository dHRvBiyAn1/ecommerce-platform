package com.project.coupon.exception;

import com.project.common.exception.ValidationException;

public class CouponUnavailableException extends ValidationException {

    public CouponUnavailableException(String message) {
        super(message);
    }
}
