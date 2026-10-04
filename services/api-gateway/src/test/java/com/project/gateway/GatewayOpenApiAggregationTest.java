package com.project.gateway;

import com.project.gateway.config.OpenApiAggregationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.DefaultServiceInstance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayOpenApiAggregationTest {

    @Test
    void publishesOnlyUrlsForCurrentlyDiscoveredInstances() {
        MutableDiscoveryClient discovery = new MutableDiscoveryClient();
        discovery.instances = List.of(instance("product-service", "products", 8081));
        OpenApiAggregationConfig config = new OpenApiAggregationConfig(discovery);

        assertThat(config.swaggerConfig().urls())
                .extracting(OpenApiAggregationConfig.OpenApiUrl::name,
                        OpenApiAggregationConfig.OpenApiUrl::url)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "product-service", "http://products:8081/v3/api-docs"));

        discovery.instances = List.of();

        assertThat(config.swaggerConfig().urls()).isEmpty();
    }

    @Test
    void omitsServicesWithoutInstances() {
        MutableDiscoveryClient discovery = new MutableDiscoveryClient();
        discovery.services = List.of("cart-service", "inventory-service");
        discovery.instances = List.of(instance("cart-service", "cart", 8082));
        OpenApiAggregationConfig config = new OpenApiAggregationConfig(discovery);

        assertThat(config.swaggerConfig().urls())
                .extracting(OpenApiAggregationConfig.OpenApiUrl::name)
                .containsExactly("cart-service");
    }

    private static ServiceInstance instance(String serviceId, String host, int port) {
        return new DefaultServiceInstance(serviceId, serviceId, host, port, false);
    }

    private static final class MutableDiscoveryClient implements DiscoveryClient {
        private List<String> services = List.of();
        private List<ServiceInstance> instances = List.of();

        @Override
        public String description() {
            return "test";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return instances.stream().filter(instance -> instance.getServiceId().equals(serviceId)).toList();
        }

        @Override
        public List<String> getServices() {
            return services.isEmpty()
                    ? instances.stream().map(ServiceInstance::getServiceId).distinct().toList()
                    : services;
        }
    }
}
