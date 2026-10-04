package com.project.cart.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ProductServiceUnavailableException extends BusinessException {

    public ProductServiceUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_SERVICE_UNAVAILABLE",
                "Product service is temporarily unavailable", cause);
    }
}
