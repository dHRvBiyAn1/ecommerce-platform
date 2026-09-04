package com.project.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSummary(
        String id,
        String orderNumber,
        UUID userId,
        String status,
        BigDecimal totalAmount,
        String currency
) {}
