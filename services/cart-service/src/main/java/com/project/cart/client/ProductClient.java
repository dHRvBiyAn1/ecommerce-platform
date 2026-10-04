package com.project.cart.client;

import com.project.common.feign.FeignAuthForwardingConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", configuration = FeignAuthForwardingConfig.class,
        fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/api/v1/products/{productId}")
    ProductSummary getProduct(@PathVariable("productId") String productId);
}
