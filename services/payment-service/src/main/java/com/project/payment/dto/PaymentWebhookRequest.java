package com.project.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookRequest {
    @NotBlank(message = "Payment reference is required")
    private String paymentReference;

    private String transactionId;

    @NotBlank(message = "Status is required")
    private String status;

    private String failureReason;
}
