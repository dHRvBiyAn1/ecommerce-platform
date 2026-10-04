package com.project.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "gateway.cors")
public class GatewayCorsProperties {
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://localhost:5174"));
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "Accept"));
    private boolean allowCredentials = true;
    private long maxAge = 3600;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        validate();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }

    public void validate() {
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("wildcard CORS origin is prohibited when credentials are enabled");
        }
    }

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> value) { allowedOrigins = value; }
    public List<String> getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(List<String> value) { allowedMethods = value; }
    public List<String> getAllowedHeaders() { return allowedHeaders; }
    public void setAllowedHeaders(List<String> value) { allowedHeaders = value; }
    public boolean isAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(boolean value) { allowCredentials = value; }
    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long value) { maxAge = value; }
}
