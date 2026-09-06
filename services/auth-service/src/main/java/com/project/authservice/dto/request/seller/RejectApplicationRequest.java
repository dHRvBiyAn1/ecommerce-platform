package com.project.authservice.dto.request.seller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApplicationRequest(@NotBlank @Size(max = 4000) String reason) {
}
