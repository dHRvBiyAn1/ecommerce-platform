package com.project.user_service.dtos;

import lombok.Data;
import java.util.UUID;

@Data
public class UserProfileDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private boolean emailVerified;
}
