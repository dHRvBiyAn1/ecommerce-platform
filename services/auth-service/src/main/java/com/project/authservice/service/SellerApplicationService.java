package com.project.authservice.service;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.dto.seller.RejectApplicationRequest;
import com.project.authservice.dto.seller.SellerApplicationRequest;
import com.project.authservice.dto.seller.SellerApplicationResponse;
import com.project.authservice.entity.Address;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.entity.User;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.SellerApplicationRepository;
import com.project.authservice.repository.UserRepository;
import com.project.common.constant.Roles;
import com.project.common.exception.ResourceNotFoundException;
import com.project.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for the seller-application workflow. Replaces the old all-or-nothing
 * "anyone with ROLE_SELLER can list products" model with a moderated path:
 * customer applies → admin reviews → either ROLE_SELLER is granted or the
 * application is rejected with a reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerApplicationService {

    private final SellerApplicationRepository repository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    // ---------------------------------------------------------------- Customer

    @Transactional(readOnly = true)
    public Optional<SellerApplicationResponse> getMine(UUID userId) {
        return repository.findByUserId(userId).map(this::toResponse);
    }

    /**
     * Submit an application. If a previous {@code REJECTED} application
     * exists, it's overwritten with the fresh details (status reset to
     * PENDING). Throws if there's already a PENDING or APPROVED application.
     */
    @Transactional
    public SellerApplicationResponse apply(UUID userId, SellerApplicationRequest req) {
        SellerApplication app = repository.findByUserId(userId).orElse(null);
        if (app != null) {
            if (app.getStatus() == SellerApplicationStatus.PENDING) {
                throw new ValidationException("You already have a pending application");
            }
            if (app.getStatus() == SellerApplicationStatus.APPROVED) {
                throw new ValidationException("You are already an approved seller");
            }
            // REJECTED → overwrite with fresh data, reset to PENDING.
            app.setStatus(SellerApplicationStatus.PENDING);
            app.setRejectionReason(null);
            app.setReviewedAt(null);
            app.setReviewedBy(null);
        } else {
            app = SellerApplication.builder().userId(userId).build();
        }
        app.setBusinessName(req.getBusinessName());
        app.setGstin(blankToNull(req.getGstin()));
        app.setContactPhone(req.getContactPhone());
        app.setPickupAddress(toEntity(req.getPickupAddress()));
        app.setBankAccountLast4(blankToNull(req.getBankAccountLast4()));
        app.setNotes(req.getNotes());
        return toResponse(repository.save(app));
    }

    // -------------------------------------------------------------------- Admin

    @Transactional(readOnly = true)
    public Page<SellerApplicationResponse> list(SellerApplicationStatus status, Pageable pageable) {
        Page<SellerApplication> page = (status == null)
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SellerApplicationResponse get(UUID applicationId) {
        return repository.findById(applicationId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("SellerApplication", applicationId));
    }

    /**
     * Approve: marks PENDING → APPROVED and grants {@code ROLE_SELLER}.
     * Idempotent: re-approving an APPROVED application is a no-op.
     */
    @Transactional
    public SellerApplicationResponse approve(UUID applicationId, UUID adminId) {
        SellerApplication app = repository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("SellerApplication", applicationId));
        if (app.getStatus() == SellerApplicationStatus.APPROVED) return toResponse(app);
        if (app.getStatus() != SellerApplicationStatus.PENDING) {
            throw new ValidationException("Only pending applications can be approved");
        }

        User user = userRepository.findById(app.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", app.getUserId()));
        Role sellerRole = roleRepository.findByName(Roles.SELLER)
                .orElseThrow(() -> new IllegalStateException("ROLE_SELLER not seeded"));
        user.getRoles().add(sellerRole);
        userRepository.save(user);

        app.setStatus(SellerApplicationStatus.APPROVED);
        app.setReviewedAt(LocalDateTime.now());
        app.setReviewedBy(adminId);
        log.info("Seller application {} approved by {} for user {}", applicationId, adminId, user.getEmail());
        return toResponse(repository.save(app));
    }

    @Transactional
    public SellerApplicationResponse reject(UUID applicationId, UUID adminId, RejectApplicationRequest req) {
        SellerApplication app = repository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("SellerApplication", applicationId));
        if (app.getStatus() != SellerApplicationStatus.PENDING) {
            throw new ValidationException("Only pending applications can be rejected");
        }
        app.setStatus(SellerApplicationStatus.REJECTED);
        app.setRejectionReason(req.getReason());
        app.setReviewedAt(LocalDateTime.now());
        app.setReviewedBy(adminId);
        log.info("Seller application {} rejected by {}", applicationId, adminId);
        return toResponse(repository.save(app));
    }

    // ----------------------------------------------------------------- Helpers

    private Address toEntity(AddressDto dto) {
        if (dto == null) return null;
        return Address.builder()
                .fullName(dto.fullName())
                .phone(dto.phone())
                .street(dto.street())
                .city(dto.city())
                .state(dto.state())
                .zipCode(dto.zipCode())
                .country(dto.country() == null ? "IN" : dto.country())
                .build();
    }

    private AddressDto toAddressDto(Address a) {
        if (a == null) return null;
        return new AddressDto(a.getFullName(), a.getPhone(), a.getStreet(), a.getCity(), a.getState(),
                a.getZipCode(), a.getCountry());
    }

    private SellerApplicationResponse toResponse(SellerApplication app) {
        // Lookup user for denormalized email/displayName fields.
        var user = userRepository.findById(app.getUserId()).orElse(null);
        return SellerApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .userEmail(user != null ? user.getEmail() : null)
                .userDisplayName(user != null ? user.getDisplayName() : null)
                .status(app.getStatus())
                .businessName(app.getBusinessName())
                .gstin(app.getGstin())
                .contactPhone(app.getContactPhone())
                .pickupAddress(toAddressDto(app.getPickupAddress()))
                .bankAccountLast4(app.getBankAccountLast4())
                .notes(app.getNotes())
                .rejectionReason(app.getRejectionReason())
                .submittedAt(app.getSubmittedAt())
                .reviewedAt(app.getReviewedAt())
                .reviewedBy(app.getReviewedBy())
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
