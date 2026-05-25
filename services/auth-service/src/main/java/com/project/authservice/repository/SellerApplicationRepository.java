package com.project.authservice.repository;

import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.SellerApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

    Optional<SellerApplication> findByUserId(UUID userId);

    Page<SellerApplication> findByStatus(SellerApplicationStatus status, Pageable pageable);
}
