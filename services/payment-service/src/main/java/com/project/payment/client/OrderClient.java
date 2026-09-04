package com.project.payment.client;

import com.project.common.dto.ApiResponse;
import com.project.payment.client.dto.OrderSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/api/v1/orders")
public interface OrderClient {

    @GetMapping("/{orderId}")
    ApiResponse<OrderSummary> getOrder(@PathVariable("orderId") String orderId);
}
