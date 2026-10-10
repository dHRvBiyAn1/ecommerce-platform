package com.project.gateway;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

class Java21ThreadingHttpTest {

  @Test
  void embeddedHttpRequestsUseTheConfiguredThreadModeAndClearObservationScope() throws Exception {
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
                  "--spring.cloud.gateway.enabled=false",
                  "--management.endpoints.enabled-by-default=false")) {
        RequestProbe probe = context.getBean(RequestProbe.class);
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        HttpResponse<String> response =
            HttpClient.newHttpClient()
                .send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/thread-check"))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(Boolean.toString(virtualThreads), response.body());
        assertTrue(probe.insideObservation.get());
        assertTrue(probe.scopeRestored.get());
        assertTrue(probe.requestFinished.await(2, TimeUnit.SECONDS));
        context.close();
        assertFalse(context.isActive());
      }
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
      })
  @Import({ThreadController.class, TraceScopeConfiguration.class})
  static class TestApplication {}

  @RestController
  @TestComponent
  static class ThreadController {
    @GetMapping("/thread-check")
    boolean threadMode() {
      return Thread.currentThread().isVirtual();
    }
  }

  static class RequestProbe {
    final AtomicBoolean insideObservation = new AtomicBoolean();
    final AtomicBoolean scopeRestored = new AtomicBoolean();
    final CountDownLatch requestFinished = new CountDownLatch(1);
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
      registration.setOrder(Ordered.LOWEST_PRECEDENCE);
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
        probe.insideObservation.set(registry.getCurrentObservation() == observation);
      } finally {
        observation.stop();
        probe.scopeRestored.set(registry.getCurrentObservation() == previous);
        probe.requestFinished.countDown();
      }
    }
  }
}
