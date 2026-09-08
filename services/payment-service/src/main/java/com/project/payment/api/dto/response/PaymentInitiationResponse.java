package com.project.payment.api.dto.response;

public record PaymentInitiationResponse(PaymentResponse payment, String clientSecret) {}
