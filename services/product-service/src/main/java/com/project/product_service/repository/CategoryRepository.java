package com.project.product_service.repository;

import com.project.product_service.model.Category;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, String> {
  Optional<Category> findByName(String name);
}
