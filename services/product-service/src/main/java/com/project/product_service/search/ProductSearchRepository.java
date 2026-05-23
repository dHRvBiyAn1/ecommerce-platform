package com.project.product_service.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

import java.util.Optional;

public class ProductSearchRepository {

    private final ElasticsearchOperations operations;

    public ProductSearchRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public void save(ProductDocument doc) {
        operations.save(doc);
    }

    public void deleteById(String id) {
        operations.delete(id, ProductDocument.class);
    }

    public Optional<ProductDocument> findById(String id) {
        return Optional.ofNullable(operations.get(id, ProductDocument.class));
    }

    public java.util.List<ProductDocument> search(String keyword, Pageable pageable) {
        Criteria criteria = new Criteria("name").contains(keyword)
                .or(new Criteria("description").contains(keyword));
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(pageable);
        return operations.search(query, ProductDocument.class).stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public long count(String keyword) {
        Criteria criteria = new Criteria("name").contains(keyword)
                .or(new Criteria("description").contains(keyword));
        CriteriaQuery query = new CriteriaQuery(criteria);
        return operations.count(query, ProductDocument.class);
    }
}
