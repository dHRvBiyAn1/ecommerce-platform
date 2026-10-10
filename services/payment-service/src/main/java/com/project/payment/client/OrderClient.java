package com.project.payment.client;

import com.project.common.generated.model.ResponseEnvelope;
import com.project.payment.generated.integration.order.model.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/api/v1/orders")
public interface OrderClient {

    @GetMapping("/{orderId}")
    ResponseEnvelope<OrderResponse> getOrder(@PathVariable("orderId") String orderId);
}
