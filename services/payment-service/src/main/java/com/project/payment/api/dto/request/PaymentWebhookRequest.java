package com.project.payment.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentWebhookRequest(
        @NotBlank(message = "Payment reference is required")
        @Size(max = 100)
        String paymentReference,
        @Size(max = 200)
        String transactionId,
        @NotBlank(message = "Status is required")
        @Size(max = 30)
        String status,
        @Size(max = 500)
        String failureReason
) {}
