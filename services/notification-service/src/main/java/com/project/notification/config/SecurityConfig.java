package com.project.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.security.JwtAuthenticationConverter;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
public class SecurityConfig {

  @Bean
  // Bearer-only stateless API; cookie/session identities and HTTP Basic are rejected by security
  // tests.
  @SuppressWarnings("java:S4502")
  public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    AuthenticationEntryPoint entryPoint =
        (request, response, exception) -> {
          response.setStatus(401);
          response.setHeader("WWW-Authenticate", "Bearer");
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          objectMapper.writeValue(
              response.getWriter(),
              Map.of(
                  "status", 401, "code", "UNAUTHENTICATED", "message", "Authentication required"));
        };
    AccessDeniedHandler deniedHandler =
        (request, response, exception) -> {
          response.setStatus(403);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          objectMapper.writeValue(
              response.getWriter(),
              Map.of("status", 403, "code", "ACCESS_DENIED", "message", "Access denied"));
        };
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
        .oauth2ResourceServer(
            o ->
                o.authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler)
                    .jwt(j -> j.jwtAuthenticationConverter(new JwtAuthenticationConverter())));
    return http.build();
  }
}
