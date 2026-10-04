package com.project.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import com.project.gateway.filter.ForwardedIpTrustFilter;
import com.project.gateway.filter.GatewayErrorResponseFilter;
import com.project.gateway.config.ClientHeaderStrippingFilter;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * API gateway. Edge concerns only:
 * <ul>
 *   <li>Routing (defined in config-repo/api-gateway.yml)</li>
 *   <li>Rate limiting (Redis-backed, see {@code application.yml})</li>
 *   <li>CORS and security filters (bound in focused configuration classes)</li>
 * </ul>
 *
 * <p>Authentication is no longer performed at the gateway. The {@code Authorization}
 * header is passed through verbatim and each backend service verifies it via JWKS.
 * This removes the broken "trust X-User-Id from any caller" model.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<ForwardedIpTrustFilter> forwardedIpTrustFilterRegistration(
            @Value("${gateway.forwarded.trusted-cidrs:}") List<String> trustedCidrs) {
        FilterRegistrationBean<ForwardedIpTrustFilter> registration =
                new FilterRegistrationBean<>(new ForwardedIpTrustFilter(trustedCidrs));
        registration.addUrlPatterns("/*");
        registration.setOrder(ForwardedIpTrustFilter.FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<GatewayErrorResponseFilter> gatewayErrorResponseFilterRegistration() {
        FilterRegistrationBean<GatewayErrorResponseFilter> registration =
                new FilterRegistrationBean<>(new GatewayErrorResponseFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(GatewayErrorResponseFilter.FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ClientHeaderStrippingFilter> headerStrippingFilterRegistration() {
        FilterRegistrationBean<ClientHeaderStrippingFilter> registration =
                new FilterRegistrationBean<>(new ClientHeaderStrippingFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(ClientHeaderStrippingFilter.FILTER_ORDER);
        return registration;
    }

}
