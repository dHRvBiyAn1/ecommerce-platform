package com.project.order.client;

import com.project.order.client.dto.ProductSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Calls product-service via Eureka load balancing. Used at order creation to
 * fetch the authoritative price/SKU/sellerId for each line item, which we
 * snapshot into the Order so later product-price changes don't rewrite history.
 */
@FeignClient(name = "product-service", path = "/api/v1/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductSummary getProduct(@PathVariable("id") String id);
}
