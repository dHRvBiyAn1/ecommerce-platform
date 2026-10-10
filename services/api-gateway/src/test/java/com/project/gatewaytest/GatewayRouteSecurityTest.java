package com.project.gatewaytest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.gateway.config.OpenApiAggregationConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@SpringJUnitWebConfig(classes = GatewayRouteSecurityTest.TestConfiguration.class)
class GatewayRouteSecurityTest {
  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void rejectsProtectedRoutesWithoutAuthorization() throws Exception {
    mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void permitsProtectedRoutesWithAuthorization() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders").header("Authorization", "Bearer signed-token"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  void rejectsProtectedRoutesWithBlankAuthorization() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders").header("Authorization", " "))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/products",
        "/api/v1/products/product-1",
        "/api/v1/products/search",
        "/api/v1/products/category/category-1",
        "/api/v1/products/filter",
        "/api/v1/categories",
        "/api/v1/categories/category-1"
      })
  void permitsGuestCatalogReads(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/products", "/api/v1/products/product-1", "/api/v1/categories"})
  void keepsCatalogWritesProtected(String path) throws Exception {
    mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
    mockMvc.perform(put(path)).andExpect(status().isUnauthorized());
    mockMvc.perform(delete(path)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/products/seller",
        "/api/v1/products/seller/seller-1",
        "/api/v1/products/admin/by-status",
        "/api/v1/cart",
        "/api/v1/orders",
        "/api/v1/products-extra",
        "/api/v1/categories-extra"
      })
  void keepsPrivateAndLookalikeReadsProtected(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }

  @Test
  void permitsPublicAuthAndDiscoveryRoutesWithoutAuthorization() throws Exception {
    mockMvc.perform(get("/api/auth/login")).andExpect(status().isOk());
    mockMvc.perform(get("/eureka/apps")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }

  @Configuration
  @EnableWebMvc
  @Import(OpenApiAggregationConfig.class)
  static class TestConfiguration {
    @Bean
    DiscoveryClient discoveryClient() {
      return new DiscoveryClient() {
        @Override
        public String description() {
          return "test";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
          return List.of();
        }

        @Override
        public List<String> getServices() {
          return List.of();
        }
      };
    }

    @Bean
    RouterFunction<ServerResponse> testRoutes() {
      return RouterFunctions.route(request -> true, request -> ServerResponse.ok().body("ok"));
    }
  }
}
