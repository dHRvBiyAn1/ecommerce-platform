package com.project.authservice.mapper;

import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.SellerApplicationResponse;
import org.springframework.stereotype.Component;

@Component
public class SellerApplicationMapper {
    private final AddressMapper addressMapper;

    public SellerApplicationMapper(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public SellerApplicationResponse toResponse(SellerApplication application, User user) {
        return new SellerApplicationResponse().id(application.getId()).userId(application.getUserId())
                .userEmail(user == null ? null : user.getEmail())
                .userDisplayName(user == null ? null : user.getDisplayName())
                .status(SellerApplicationResponse.StatusEnum.fromValue(application.getStatus().name()))
                .businessName(application.getBusinessName()).gstin(application.getGstin())
                .contactPhone(application.getContactPhone())
                .pickupAddress(addressMapper.toDto(application.getPickupAddress()))
                .bankAccountLast4(application.getBankAccountLast4()).notes(application.getNotes())
                .rejectionReason(application.getRejectionReason()).submittedAt(application.getSubmittedAt())
                .reviewedAt(application.getReviewedAt()).reviewedBy(application.getReviewedBy());
    }
}
