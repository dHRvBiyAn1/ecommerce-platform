package com.project.authservice.dto.response.seller;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.entity.SellerApplicationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SellerApplicationResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String userDisplayName,
        SellerApplicationStatus status,
        String businessName,
        String gstin,
        String contactPhone,
        AddressDto pickupAddress,
        String bankAccountLast4,
        String notes,
        String rejectionReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        UUID reviewedBy) {
}
