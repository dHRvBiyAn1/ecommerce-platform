package com.project.authservice.dto.seller;

import com.project.authservice.dto.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SellerApplicationRequest {

    @NotBlank
    @Size(max = 180)
    private String businessName;

    /** Indian GSTIN: 15 chars, mostly digits + state code. Pattern is loose; service can verify. */
    @Pattern(regexp = "^[0-9A-Z]{15}$|^$", message = "GSTIN must be 15 alphanumeric characters")
    private String gstin;

    @NotBlank
    @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "Invalid phone number")
    private String contactPhone;

    @Valid
    @NotNull
    private AddressDto pickupAddress;

    @Pattern(regexp = "^[0-9]{4}$|^$", message = "Last-4 of bank account must be 4 digits")
    private String bankAccountLast4;

    @Size(max = 4000)
    private String notes;
}
