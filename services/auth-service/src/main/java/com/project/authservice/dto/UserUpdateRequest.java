package com.project.authservice.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String displayName;
    private String imageUrl;
}
