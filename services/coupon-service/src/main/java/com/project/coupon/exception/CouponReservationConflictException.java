package com.project.coupon.exception;

import com.project.common.exception.DuplicateResourceException;

public class CouponReservationConflictException extends DuplicateResourceException {

    public CouponReservationConflictException(String message) {
        super(message);
    }
}
