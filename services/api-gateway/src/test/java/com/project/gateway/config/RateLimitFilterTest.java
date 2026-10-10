package com.project.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.gateway.filter.ForwardedIpTrustFilter;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitFilterTest {
  @ParameterizedTest
  @CsvSource({
    "/api/auth/token,auth,20,false",
    "/api/v1/cart,default,100,false",
    "/api/v1/cart,default,100,true"
  })
  void enforcesSeparateLimitsUsingTheTrustedClientAddress(
      String path, String bucket, long capacity, boolean allowed) throws Exception {
    @SuppressWarnings("unchecked")
    ProxyManager<byte[]> manager = mock(ProxyManager.class, RETURNS_DEEP_STUBS);
    when(manager.builder().build(any(byte[].class), any(BucketConfiguration.class)).tryConsume(1))
        .thenReturn(allowed);
    org.mockito.Mockito.clearInvocations(manager.builder());
    SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    RateLimitConfig configuration = new RateLimitConfig(metrics);
    ReflectionTestUtils.setField(configuration, "authCapacity", 20L);
    ReflectionTestUtils.setField(configuration, "authRefill", 20L);
    ReflectionTestUtils.setField(configuration, "authPeriodSec", 60L);
    ReflectionTestUtils.setField(configuration, "defaultCapacity", 100L);
    ReflectionTestUtils.setField(configuration, "defaultRefill", 100L);
    ReflectionTestUtils.setField(configuration, "defaultPeriodSec", 60L);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr("10.0.0.1");
    request.setAttribute(ForwardedIpTrustFilter.CLIENT_IP_ATTRIBUTE, "198.51.100.10");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean forwarded = new AtomicBoolean();

    configuration
        .rateLimitFilterRegistration(manager)
        .getFilter()
        .doFilter(request, response, (req, res) -> forwarded.set(true));

    assertThat(forwarded.get()).isEqualTo(allowed);
    ArgumentCaptor<byte[]> key = ArgumentCaptor.forClass(byte[].class);
    ArgumentCaptor<BucketConfiguration> policy = ArgumentCaptor.forClass(BucketConfiguration.class);
    verify(manager.builder()).build(key.capture(), policy.capture());
    assertThat(new String(key.getValue(), StandardCharsets.UTF_8))
        .isEqualTo("rl:198.51.100.10:" + bucket);
    assertThat(policy.getValue().getBandwidths()[0].getCapacity()).isEqualTo(capacity);
    if (!allowed) {
      assertThat(response.getStatus()).isEqualTo(429);
      assertThat(response.getContentType()).isEqualTo("application/json");
      assertThat(response.getContentAsString())
          .contains("Too many requests", "Rate limit exceeded.");
      assertThat(
              metrics
                  .get("gateway.rate_limit.blocked")
                  .tags("ip", "198.51.100.10", "path", path)
                  .counter()
                  .count())
          .isEqualTo(1);
    } else {
      assertThat(metrics.find("gateway.rate_limit.blocked").counter()).isNull();
    }
  }
}
