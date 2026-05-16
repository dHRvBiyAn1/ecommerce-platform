package com.project.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    private String orderNumber;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String currency;

    private String description;
}
