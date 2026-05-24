package com.project.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API gateway. Edge concerns only:
 * <ul>
 *   <li>Routing (defined in config-repo/api-gateway.yml)</li>
 *   <li>Rate limiting (Redis-backed, see {@code application.yml})</li>
 *   <li>CORS (delegated to each backend service)</li>
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
}
