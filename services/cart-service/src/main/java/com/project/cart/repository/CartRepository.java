package com.project.cart.repository;

import com.project.cart.model.Cart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CartRepository extends MongoRepository<Cart, String> {

  Optional<Cart> findByUserId(UUID userId);

  void deleteByUserId(UUID userId);
}
