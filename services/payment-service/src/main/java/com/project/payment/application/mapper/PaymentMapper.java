package com.project.payment.application.mapper;

import com.project.payment.generated.model.PaymentResponse;
import com.project.payment.model.Payment;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    implementationPackage = "com.project.payment.generated.mapper",
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface PaymentMapper {
  @org.mapstruct.Mapping(
      target = "refundedAmount",
      defaultExpression = "java(java.math.BigDecimal.ZERO)")
  PaymentResponse toResponse(Payment payment);
}
