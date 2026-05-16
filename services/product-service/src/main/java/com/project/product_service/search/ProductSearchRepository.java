package com.project.product_service.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    Optional<ProductDocument> findById(String id);
}
