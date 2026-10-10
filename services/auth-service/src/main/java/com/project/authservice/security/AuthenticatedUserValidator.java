package com.project.authservice.security;

import com.project.authservice.exception.AuthException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserValidator {

  public UUID requireUserId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new AuthException("Authentication required");
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException exception) {
      throw new AuthException("Invalid authenticated user");
    }
  }
}
