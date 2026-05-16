package com.project.inventory.exception;

import lombok.Getter;

@Getter
public class ResourceNotFoundException extends RuntimeException {
    private final String productId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.productId = null;
    }

    public ResourceNotFoundException(String message, String productId) {
        super(message);
        this.productId = productId;
    }
}
