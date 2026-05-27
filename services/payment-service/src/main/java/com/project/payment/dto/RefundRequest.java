package com.project.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * @param reason free-text reason
 * @param amount partial-refund amount, or {@code null} for full refund
 */
public record RefundRequest(
        @NotBlank String reason,
        @PositiveOrZero BigDecimal amount
) {}
