package com.project.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    private String couponCode;

    @Valid
    private ShippingAddressRequest shippingAddress;

    @Valid
    private BillingAddressRequest billingAddress;

    private String notes;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}
