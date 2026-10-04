package com.project.payment.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        @NotBlank(message = "Refund reason is required")
        @Size(max = 500, message = "Refund reason must not exceed 500 characters")
        String reason,
        @Positive(message = "Refund amount must be positive")
        BigDecimal amount
) {}
