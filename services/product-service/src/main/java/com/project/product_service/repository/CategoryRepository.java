package com.project.product_service.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.project.product_service.model.Category;

import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findByName(String name);
}
