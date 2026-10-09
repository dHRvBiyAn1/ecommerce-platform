package com.project.authservice.mapper;

import com.project.authservice.entity.Address;
import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.SellerApplicationResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SellerApplicationMapperTest {
    private final SellerApplicationMapper mapper = new SellerApplicationMapper(new AddressMapper());

    @Test
    void mapsApplicationAndResolvedUserToGeneratedResponse() {
        UUID applicationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reviewerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        LocalDateTime submitted = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime reviewed = LocalDateTime.of(2025, 1, 3, 4, 5, 6);
        SellerApplication application = SellerApplication.builder().id(applicationId).userId(userId)
                .status(SellerApplicationStatus.REJECTED).businessName("Acme").gstin("27ABCDE1234F1Z5")
                .contactPhone("+91 9876543210")
                .pickupAddress(Address.builder().fullName("Asha").phone("+91 9876543210").street("1 Main St")
                        .city("Pune").state("MH").zipCode("411001").country("IN").build())
                .bankAccountLast4("1234").notes("Call first").rejectionReason("Missing documents")
                .submittedAt(submitted).reviewedAt(reviewed).reviewedBy(reviewerId).build();
        User user = new User();
        user.setEmail("seller@example.com");
        user.setDisplayName("Seller");

        SellerApplicationResponse response = mapper.toResponse(application, user);

        assertThat(response.getId()).isEqualTo(applicationId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUserEmail()).isEqualTo("seller@example.com");
        assertThat(response.getUserDisplayName()).isEqualTo("Seller");
        assertThat(response.getStatus()).isEqualTo(SellerApplicationResponse.StatusEnum.REJECTED);
        assertThat(response.getPickupAddress().getCountry()).isEqualTo("IN");
        assertThat(response.getRejectionReason()).isEqualTo("Missing documents");
        assertThat(response.getSubmittedAt()).isEqualTo(submitted);
        assertThat(response.getReviewedAt()).isEqualTo(reviewed);
        assertThat(response.getReviewedBy()).isEqualTo(reviewerId);
    }

    @Test
    void mapsMissingResolvedUserAndPickupAddressAsNull() {
        SellerApplication application = SellerApplication.builder()
                .userId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .status(SellerApplicationStatus.PENDING).businessName("Acme").contactPhone("+91 9876543210").build();

        SellerApplicationResponse response = mapper.toResponse(application, null);

        assertThat(response.getUserEmail()).isNull();
        assertThat(response.getUserDisplayName()).isNull();
        assertThat(response.getStatus()).isEqualTo(SellerApplicationResponse.StatusEnum.PENDING);
        assertThat(response.getPickupAddress()).isNull();
    }
}
