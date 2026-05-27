package com.project.authservice.dto.seller;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.entity.SellerApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerApplicationResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userDisplayName;
    private SellerApplicationStatus status;
    private String businessName;
    private String gstin;
    private String contactPhone;
    private AddressDto pickupAddress;
    private String bankAccountLast4;
    private String notes;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
}
