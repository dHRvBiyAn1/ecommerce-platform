package com.project.payment.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank(message = "Order ID is required")
        @Size(max = 100, message = "Order ID must not exceed 100 characters")
        String orderId,
        @Size(max = 100, message = "Order number must not exceed 100 characters")
        String orderNumber,
        @NotBlank(message = "Payment method is required")
        @Size(max = 50, message = "Payment method must not exceed 50 characters")
        String paymentMethod,
        @Positive(message = "Amount must be positive")
        BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter uppercase code")
        String currency,
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description
) {}
