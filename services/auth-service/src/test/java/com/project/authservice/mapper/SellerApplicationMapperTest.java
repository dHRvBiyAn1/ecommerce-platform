package com.project.authservice.mapper;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.dto.response.seller.SellerApplicationResponse;
import com.project.authservice.entity.Address;
import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SellerApplicationMapperTest {

    @Test
    void mapstructGeneratesSpringMapperThatMapsApplicationWithResolvedUserAndPickupAddress() throws Exception {
        Class<?> generatedMapper = Class.forName("com.project.authservice.mapper.SellerApplicationMapperImpl");
        assertThat(SellerApplicationMapper.class.isInterface()).isTrue();
        assertThat(generatedMapper.isAnnotationPresent(Component.class)).isTrue();

        UUID applicationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reviewerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        SellerApplication application = SellerApplication.builder()
                .id(applicationId).userId(userId).status(SellerApplicationStatus.REJECTED)
                .businessName("Acme").gstin("27ABCDE1234F1Z5").contactPhone("+91 9876543210")
                .pickupAddress(Address.builder().fullName("Asha").phone("+91 9876543210").street("1 Main St")
                        .city("Pune").state("MH").zipCode("411001").country("IN").build())
                .bankAccountLast4("1234").notes("Call first").rejectionReason("Missing documents")
                .submittedAt(LocalDateTime.of(2025, 1, 2, 3, 4, 5))
                .reviewedAt(LocalDateTime.of(2025, 1, 3, 4, 5, 6)).reviewedBy(reviewerId).build();
        User user = new User();
        user.setEmail("seller@example.com");
        user.setDisplayName("Seller");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AddressMapper.class, generatedMapper);
            context.refresh();
            SellerApplicationResponse response = context.getBean(SellerApplicationMapper.class).toResponse(application, user);

            assertThat(response).isEqualTo(new SellerApplicationResponse(applicationId, userId, "seller@example.com", "Seller",
                    SellerApplicationStatus.REJECTED, "Acme", "27ABCDE1234F1Z5", "+91 9876543210",
                    new AddressDto("Asha", "+91 9876543210", "1 Main St", "Pune", "MH", "411001", "IN"),
                    "1234", "Call first", "Missing documents", LocalDateTime.of(2025, 1, 2, 3, 4, 5),
                    LocalDateTime.of(2025, 1, 3, 4, 5, 6), reviewerId));
        }
    }

    @Test
    void generatedMapperAllowsMissingResolvedUserAndPickupAddress() throws Exception {
        SellerApplication application = SellerApplication.builder()
                .userId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .status(SellerApplicationStatus.PENDING)
                .businessName("Acme")
                .contactPhone("+91 9876543210")
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AddressMapper.class,
                    Class.forName("com.project.authservice.mapper.SellerApplicationMapperImpl"));
            context.refresh();
            SellerApplicationResponse response = context.getBean(SellerApplicationMapper.class).toResponse(application, null);

            assertThat(response).isEqualTo(new SellerApplicationResponse(null,
                    UUID.fromString("22222222-2222-2222-2222-222222222222"), null, null,
                    SellerApplicationStatus.PENDING, "Acme", null, "+91 9876543210", null,
                    null, null, null, null, null, null));
        }
    }
}
