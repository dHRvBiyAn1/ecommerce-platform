package com.project.authservice.repository;

import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    Optional<UserCredential> findByUserIdAndAuthProvider(UUID userId, AuthProvider authProvider);
}
