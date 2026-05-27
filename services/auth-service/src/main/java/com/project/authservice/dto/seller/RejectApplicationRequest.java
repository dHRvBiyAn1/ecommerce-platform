package com.project.authservice.dto.seller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectApplicationRequest {

    @NotBlank
    @Size(max = 4000)
    private String reason;
}
