package com.project.gatewaytest;

import com.project.gateway.config.OpenApiAggregationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.GET;

@SpringJUnitWebConfig(classes = GatewayRouteSecurityTest.TestConfiguration.class)
class GatewayRouteSecurityTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void rejectsProtectedRoutesWithoutAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsProtectedRoutesWithAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer signed-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void rejectsProtectedRoutesWithBlankAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsOnlyPublicAuthAndDiscoveryRoutesWithoutAuthorization() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/eureka/apps"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
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
            return RouterFunctions.route(GET("/api/v1/orders")
                            .or(GET("/api/auth/login"))
                            .or(GET("/eureka/apps")),
                    request -> ServerResponse.ok().body("ok"));
        }
    }
}
