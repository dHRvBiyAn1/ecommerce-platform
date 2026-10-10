package com.project.product_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

class Java21ThreadingHttpTest {
  @Test
  void embeddedHttpRequestsPreserveRequestIsolationThreadModeAndScopeCleanup() throws Exception {
    for (boolean virtualThreads : new boolean[] {false, true}) {
      try (var context =
          new SpringApplicationBuilder(TestApplication.class)
              .web(WebApplicationType.SERVLET)
              .run(
                  "--server.port=0",
                  "--spring.main.keep-alive=true",
                  "--spring.threads.virtual.enabled=" + virtualThreads,
                  "--spring.cloud.config.enabled=false",
                  "--spring.cloud.config.import-check.enabled=false",
                  "--spring.cloud.discovery.enabled=false",
                  "--eureka.client.enabled=false",
                  "--management.endpoints.enabled-by-default=false")) {
        RequestProbe probe = context.getBean(RequestProbe.class);
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<String> alice = get(client, port, "alice:secret");
        CompletableFuture<String> bob = get(client, port, "bob:secret");
        CompletableFuture<String> anonymous = get(client, port, null);
        assertEquals("alice|" + virtualThreads + "|true", alice.get(5, TimeUnit.SECONDS));
        assertEquals("bob|" + virtualThreads + "|true", bob.get(5, TimeUnit.SECONDS));
        assertEquals(
            "anonymousUser|" + virtualThreads + "|true", anonymous.get(5, TimeUnit.SECONDS));
        assertTrue(probe.requestFinished.await(2, TimeUnit.SECONDS));
        assertEquals(3, probe.clearedSecurityContexts.get());
        assertEquals(3, probe.restoredObservationScopes.get());
        context.close();
        assertFalse(context.isActive());
      }
    }
  }

  private static CompletableFuture<String> get(HttpClient client, int port, String credentials) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/thread-check"));
    if (credentials != null) {
      String encoded =
          Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      request.header("Authorization", "Basic " + encoded);
    }
    return client
        .sendAsync(request.GET().build(), HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              assertEquals(200, response.statusCode());
              return response.body();
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
      })
  @Import({ThreadController.class, TestSecurityConfiguration.class, TraceScopeConfiguration.class})
  static class TestApplication {}

  @RestController
  @TestComponent
  static class ThreadController {
    private final ObservationRegistry registry;

    ThreadController(ObservationRegistry registry) {
      this.registry = registry;
    }

    @GetMapping("/thread-check")
    String requestState() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      String user = auth == null ? "none" : auth.getName();
      return user
          + "|"
          + Thread.currentThread().isVirtual()
          + "|"
          + (registry.getCurrentObservation() != null);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestSecurityConfiguration {
    @Bean
    InMemoryUserDetailsManager users() {
      return new InMemoryUserDetailsManager(
          User.withUsername("alice").password("{noop}secret").roles("USER").build(),
          User.withUsername("bob").password("{noop}secret").roles("USER").build());
    }

    @Bean
    SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .httpBasic(Customizer.withDefaults())
          .build();
    }
  }

  static class RequestProbe {
    final CountDownLatch requestFinished = new CountDownLatch(3);
    final AtomicInteger clearedSecurityContexts = new AtomicInteger();
    final AtomicInteger restoredObservationScopes = new AtomicInteger();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TraceScopeConfiguration {
    @Bean
    RequestProbe requestProbe() {
      return new RequestProbe();
    }

    @Bean
    FilterRegistrationBean<ObservationScopeFilter> observationScopeFilter(
        ObservationRegistry registry, RequestProbe probe) {
      var registration = new FilterRegistrationBean<>(new ObservationScopeFilter(registry, probe));
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
      return registration;
    }
  }

  static class ObservationScopeFilter extends OncePerRequestFilter {
    private final ObservationRegistry registry;
    private final RequestProbe probe;

    ObservationScopeFilter(ObservationRegistry registry, RequestProbe probe) {
      this.registry = registry;
      this.probe = probe;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      Observation previous = registry.getCurrentObservation();
      Observation observation = Observation.start("test-http-request", registry);
      try (var scope = observation.openScope()) {
        filterChain.doFilter(request, response);
      } finally {
        observation.stop();
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
          probe.clearedSecurityContexts.incrementAndGet();
        }
        if (registry.getCurrentObservation() == previous) {
          probe.restoredObservationScopes.incrementAndGet();
        }
        probe.requestFinished.countDown();
      }
    }
  }
}
