package com.project.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderRequest(
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    List<OrderItemRequest> items,

    String couponCode,

    @Valid
    ShippingAddressRequest shippingAddress,

    @Valid
    BillingAddressRequest billingAddress,

    String notes,

    @NotBlank(message = "Payment method is required")
    String paymentMethod
) {}
