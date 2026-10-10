package com.project.product_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.product_service.config.ElasticsearchConfig;
import com.project.product_service.model.ProductApprovalStatus;
import com.project.product_service.search.ProductDocument;
import com.project.product_service.search.ProductSearchRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@EnabledIfEnvironmentVariable(named = "PRODUCT_ELASTICSEARCH_INTEGRATION", matches = "true")
class ProductElasticsearchIntegrationTest {
  @Test
  void indexesSearchesAndDeletesWithTheConfiguredElasticsearch9Client() {
    try (var elastic =
        new GenericContainer<>(
                "docker.elastic.co/elasticsearch/elasticsearch:9.4.5@sha256:b146294881f6bf3961a1d9e952ad5751f68fe0cc816d7e9b8ebf4891fddd9d05")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2))) {
      elastic.start();
      try (var context = new AnnotationConfigApplicationContext()) {
        context
            .getEnvironment()
            .getPropertySources()
            .addFirst(
                new MapPropertySource(
                    "elastic-test",
                    Map.of(
                        "elasticsearch.host",
                        elastic.getHost(),
                        "elasticsearch.port",
                        elastic.getMappedPort(9200))));
        context.register(ElasticsearchConfig.class);
        context.refresh();
        var operations = context.getBean(ElasticsearchOperations.class);
        var indexes = operations.indexOps(ProductDocument.class);
        indexes.create();
        indexes.putMapping(indexes.createMapping());
        var repository = context.getBean(ProductSearchRepository.class);
        var document = new ProductDocument();
        document.setId("boot4-search");
        document.setName("Migration camera");
        document.setDescription("Search integration fixture");
        document.setPrice(new BigDecimal("19.95"));
        document.setSellerId(UUID.randomUUID());
        document.setActive(true);
        document.setApprovalStatus(ProductApprovalStatus.APPROVED);
        document.setAttributes(Map.of("color", "black"));
        repository.save(document);
        indexes.refresh();

        var saved = repository.findById(document.getId()).orElseThrow();
        assertThat(saved.getSellerId()).isEqualTo(document.getSellerId());
        assertThat(saved.getPrice()).isEqualByComparingTo(document.getPrice());
        assertThat(saved.getApprovalStatus()).isEqualTo(ProductApprovalStatus.APPROVED);
        assertThat(saved.getAttributes()).containsEntry("color", "black");
        assertThat(repository.search("camera", PageRequest.of(0, 10)))
            .extracting(ProductDocument::getId)
            .containsExactly(document.getId());
        assertThat(repository.count("camera")).isEqualTo(1);

        repository.deleteById(document.getId());
        indexes.refresh();
        assertThat(repository.findById(document.getId())).isEmpty();
        assertThat(repository.count("camera")).isZero();
      }
    }
  }
}
