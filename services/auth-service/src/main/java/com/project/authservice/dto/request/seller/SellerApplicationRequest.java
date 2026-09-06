package com.project.authservice.dto.request.seller;

import com.project.authservice.dto.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerApplicationRequest(
        @NotBlank @Size(max = 180) String businessName,
        @Pattern(regexp = "^[0-9A-Z]{15}$|^$", message = "GSTIN must be 15 alphanumeric characters") String gstin,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "Invalid phone number") String contactPhone,
        @Valid @NotNull AddressDto pickupAddress,
        @Pattern(regexp = "^[0-9]{4}$|^$", message = "Last-4 of bank account must be 4 digits") String bankAccountLast4,
        @Size(max = 4000) String notes) {
}
