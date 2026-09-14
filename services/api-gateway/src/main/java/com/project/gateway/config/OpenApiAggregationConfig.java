package com.project.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Comparator;
import java.util.List;

@Configuration
@RestController
public class OpenApiAggregationConfig implements WebMvcConfigurer {
    private final DiscoveryClient discoveryClient;

    public OpenApiAggregationConfig(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/v3/api-docs/swagger-config")
    public SwaggerConfig swaggerConfig() {
        List<OpenApiUrl> urls = discoveryClient.getServices().stream()
                .flatMap(serviceId -> discoveryClient.getInstances(serviceId).stream().findFirst()
                        .map(instance -> new OpenApiUrl(instance.getServiceId(), apiDocsUrl(instance))).stream())
                .sorted(Comparator.comparing(OpenApiUrl::name))
                .toList();
        return new SwaggerConfig(urls);
    }

    private static String apiDocsUrl(ServiceInstance instance) {
        return instance.getUri().resolve("/v3/api-docs").toString();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RouteAuthorizationInterceptor());
    }

    public record SwaggerConfig(List<OpenApiUrl> urls) { }

    public record OpenApiUrl(String name, String url) { }

    public static final class RouteAuthorizationInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            String authorization = request.getHeader("Authorization");
            if (isPublic(request.getRequestURI()) || (authorization != null && !authorization.isBlank())) {
                return true;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        private static boolean isPublic(String path) {
            return path.startsWith("/api/auth/") || path.startsWith("/eureka/");
        }
    }
}
