package com.project.order.repository;

import com.project.order.model.Order;
import com.project.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends MongoRepository<Order, String> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
