package com.project.cart.repository;

import com.project.cart.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends MongoRepository<Cart, String> {

    Optional<Cart> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
