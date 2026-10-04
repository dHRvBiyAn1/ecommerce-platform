package com.project.cart.client;

import com.project.cart.exception.ProductServiceUnavailableException;
import com.project.common.exception.ResourceNotFoundException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return productId -> {
            if (isNotFound(cause)) {
                throw new ResourceNotFoundException("Product", productId);
            }
            log.warn("Product service unavailable for product {}: {}", productId, cause.getMessage());
            throw new ProductServiceUnavailableException(cause);
        };
    }

    private boolean isNotFound(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof FeignException feign && feign.status() == 404) return true;
        }
        return false;
    }
}
