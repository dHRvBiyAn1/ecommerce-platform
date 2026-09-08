package com.project.gateway;

import com.project.gateway.config.ClientHeaderStrippingFilter;
import com.project.gateway.config.GatewayCorsProperties;
import com.project.gateway.filter.ForwardedIpTrustFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewaySecurityFilterTest {

    @Test
    void ignoresForwardedIpFromUntrustedPeer() throws Exception {
        MockHttpServletRequest request = request("198.51.100.10");
        request.addHeader("X-Forwarded-For", "10.0.0.8");

        new ForwardedIpTrustFilter(List.of("10.0.0.0/8")).doFilter(
                request, new MockHttpServletResponse(), (req, res) ->
                        assertThat(req.getAttribute(ForwardedIpTrustFilter.CLIENT_IP_ATTRIBUTE))
                                 .isEqualTo("198.51.100.10"));
    }

    @Test
    void removesUntrustedForwardedHeadersFromDownstreamEnumeration() throws Exception {
        MockHttpServletRequest request = request("198.51.100.10");
        request.addHeader("X-Forwarded-For", "10.0.0.8");
        request.addHeader("X-Request-Id", "request-123");

        new ForwardedIpTrustFilter(List.of("10.0.0.0/8")).doFilter(
                request, new MockHttpServletResponse(), (req, res) -> {
                    jakarta.servlet.http.HttpServletRequest downstreamRequest =
                            (jakarta.servlet.http.HttpServletRequest) req;
                    assertThat(downstreamRequest.getHeader("x-forwarded-for")).isNull();
                    assertThat(java.util.Collections.list(downstreamRequest.getHeaders("X-FORWARDED-FOR")))
                            .isEmpty();
                    assertThat(java.util.Collections.list(downstreamRequest.getHeaderNames()))
                            .noneMatch(name -> name.equalsIgnoreCase("x-forwarded-for"))
                            .contains("X-Request-Id");
                });
    }

    @Test
    void extractsForwardedIpOnlyFromTrustedProxy() throws Exception {
        MockHttpServletRequest request = request("10.0.0.12");
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.12");

        new ForwardedIpTrustFilter(List.of("10.0.0.0/8")).doFilter(
                request, new MockHttpServletResponse(), (req, res) ->
                        assertThat(req.getAttribute(ForwardedIpTrustFilter.CLIENT_IP_ATTRIBUTE))
                                .isEqualTo("198.51.100.10"));
    }

    @Test
    void appliesNonOctetCidrBoundaries() throws Exception {
        assertClientIp("172.16.0.1", "203.0.113.9", "172.16.0.0/12");
        assertClientIp("172.31.255.254", "203.0.113.9", "172.16.0.0/12");
        assertClientIp("172.32.0.1", "172.32.0.1", "172.16.0.0/12");
    }

    @Test
    void rejectsMalformedOrOverlongForwardedAddressLists() throws Exception {
        assertClientIp("10.0.0.12", "10.0.0.12", "10.0.0.0/8", "198.51.100.1,not-an-ip");
        assertClientIp("10.0.0.12", "10.0.0.12", "10.0.0.0/8",
                "198.51.100.1,198.51.100.2,198.51.100.3,198.51.100.4,198.51.100.5,198.51.100.6,198.51.100.7,198.51.100.8,198.51.100.9,198.51.100.10,198.51.100.11,198.51.100.12,198.51.100.13,198.51.100.14,198.51.100.15,198.51.100.16,198.51.100.17");
    }

    @Test
    void rejectsNonCanonicalIpv4ForwardedAddresses() throws Exception {
        assertClientIp("10.0.0.12", "10.0.0.12", "10.0.0.0/8", "010.000.000.001");
    }

    @Test
    void rejectsNonCanonicalIpv4TrustedCidrAddresses() throws Exception {
        assertClientIp("10.0.0.12", "10.0.0.12", "010.000.000.000/8", "198.51.100.10");
    }

    @Test
    void acceptsCanonicalIpv4AndIpv6Addresses() throws Exception {
        assertClientIp("0.0.0.0", "198.51.100.10", "0.0.0.0/0", "198.51.100.10");
        assertClientIp("10.0.0.1", "198.51.100.10", "10.0.0.0/8", "198.51.100.10");
        assertClientIp("2001:db8::2", "2001:db8:1::1", "2001:db8::/32", "2001:db8:1::1");
    }

    @Test
    void rejectsHostnameAndHexHostForwardedTokens() throws Exception {
        assertClientIp("10.0.0.12", "10.0.0.12", "10.0.0.0/8", "localhost");
        assertClientIp("10.0.0.12", "10.0.0.12", "10.0.0.0/8", "0x7f000001");
    }

    @Test
    void stripsClientIdentityAndForwardingHeaders() throws Exception {
        MockHttpServletRequest request = request("198.51.100.10");
        request.addHeader("X-User-Id", "admin");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        new ForwardedIpTrustFilter(List.of()).doFilter(request, new MockHttpServletResponse(),
                (forwardedRequest, forwardedResponse) -> new ClientHeaderStrippingFilter().doFilter(
                forwardedRequest, forwardedResponse, (identityRequest, identityResponse) -> {
                    assertThat(((jakarta.servlet.http.HttpServletRequest) identityRequest).getHeader("X-User-Id")).isNull();
                    assertThat(((jakarta.servlet.http.HttpServletRequest) identityRequest).getHeader("X-Forwarded-For")).isNull();
                }));
    }

    @Test
    void rejectsWildcardOriginWhenCredentialsAreEnabled() {
        GatewayCorsProperties properties = new GatewayCorsProperties();
        properties.setAllowedOrigins(List.of("*"));
        properties.setAllowCredentials(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void returnsSafeCommonErrorBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new com.project.gateway.filter.GatewayErrorResponseFilter().doFilter(
                new MockHttpServletRequest(new MockServletContext()), response,
                (req, res) -> { throw new IllegalStateException("database password=secret"); });

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"error\":\"internal_server_error\",\"message\":\"An unexpected error occurred.\"}");
        assertThat(response.getContentAsString()).doesNotContain("secret");
    }

    @Test
    void formatsGatewayGeneratedErrorStatusWithoutReplacingIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new com.project.gateway.filter.GatewayErrorResponseFilter().doFilter(
                request("198.51.100.10"), response,
                (req, res) -> ((MockHttpServletResponse) res).setStatus(404));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString()).contains("not_found");
    }

    @Test
    void leavesCommittedGatewayResponseUntouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new com.project.gateway.filter.GatewayErrorResponseFilter().doFilter(
                request("198.51.100.10"), response,
                (req, res) -> {
                    MockHttpServletResponse servletResponse = (MockHttpServletResponse) res;
                    servletResponse.setStatus(502);
                    servletResponse.getWriter().write("upstream response");
                    servletResponse.flushBuffer();
                });

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).isEqualTo("upstream response");
    }

    @Test
    void hasOneEffectiveCorsFilterRegistration() {
        assertThat(GatewayApplication.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType().equals(CorsFilter.class));
        assertThat(GatewayCorsProperties.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("corsFilterRegistration"))
                .hasSize(1);
        assertThat(GatewayApplication.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().endsWith("FilterRegistration"))
                .hasSize(3);
    }

    @Test
    void applicationContextContainsEachHardeningRegistrationExactlyOnce() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                "gateway.rate-limit.redis.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false").applyTo(context);
        context.register(GatewayApplication.class, TestInfrastructure.class);
        context.refresh();
        try (context) {
            Set<String> hardeningTypes = Set.of(
                    ForwardedIpTrustFilter.class.getName(),
                    ClientHeaderStrippingFilter.class.getName(),
                    com.project.gateway.filter.GatewayErrorResponseFilter.class.getName(),
                    CorsFilter.class.getName(),
                    "com.project.gateway.config.RateLimitConfig$RateLimitFilter");
            List<FilterRegistrationBean> hardeningRegistrations = context
                    .getBeansOfType(FilterRegistrationBean.class).values().stream()
                    .filter(registration -> hardeningTypes.contains(registration.getFilter().getClass().getName()))
                    .toList();
            assertThat(hardeningRegistrations)
                    .extracting(registration -> registration.getFilter().getClass().getName())
                    .containsExactlyInAnyOrder(
                            ForwardedIpTrustFilter.class.getName(),
                            ClientHeaderStrippingFilter.class.getName(),
                            com.project.gateway.filter.GatewayErrorResponseFilter.class.getName(),
                            CorsFilter.class.getName(),
                            "com.project.gateway.config.RateLimitConfig$RateLimitFilter");
            assertThat(hardeningRegistrations.stream().sorted(java.util.Comparator.comparingInt(FilterRegistrationBean::getOrder))
                    .map(registration -> registration.getFilter().getClass().getName()).toList())
                    .containsExactly(
                            ForwardedIpTrustFilter.class.getName(),
                            ClientHeaderStrippingFilter.class.getName(),
                            "com.project.gateway.config.RateLimitConfig$RateLimitFilter",
                            CorsFilter.class.getName(),
                            com.project.gateway.filter.GatewayErrorResponseFilter.class.getName());
            assertThat(context.getBean("forwardedIpTrustFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isEqualTo(ForwardedIpTrustFilter.FILTER_ORDER);
            assertThat(context.getBean("rateLimitFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isEqualTo(com.project.gateway.config.RateLimitConfig.RATE_LIMIT_FILTER_ORDER);
            assertThat(context.getBean("headerStrippingFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isGreaterThan(ForwardedIpTrustFilter.FILTER_ORDER)
                    .isLessThan(com.project.gateway.config.RateLimitConfig.RATE_LIMIT_FILTER_ORDER);
            assertThat(context.getBean("corsFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isGreaterThan(com.project.gateway.config.RateLimitConfig.RATE_LIMIT_FILTER_ORDER)
                    .isLessThan(Integer.MAX_VALUE);
            assertThat(context.getBean("gatewayErrorResponseFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isGreaterThan(context.getBean("corsFilterRegistration", FilterRegistrationBean.class).getOrder());
            assertThat(new ServletContextInitializerBeans(context))
                    .filteredOn(initializer -> initializer instanceof FilterRegistrationBean<?> registration
                            && hardeningTypes.contains(registration.getFilter().getClass().getName()))
                    .hasSize(5);
        }
    }

    @Test
    void externalCorsConfigurationHasNoWildcardHeaders() throws Exception {
        String config = java.nio.file.Files.readString(java.nio.file.Path.of("../../config-repo/api-gateway.yml"));
        assertThat(config).doesNotContain("allowedHeaders:\n             - \"*\"");
        assertThat(config).contains("allowed-headers: Authorization,Content-Type,Accept");
        assertThat(config).contains("allow-credentials: true");
    }

    private static void assertClientIp(String peer, String expected, String cidr) throws Exception {
        assertClientIp(peer, expected, cidr, "203.0.113.9");
    }

    private static void assertClientIp(String peer, String expected, String cidr, String forwarded) throws Exception {
        MockHttpServletRequest request = request(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        new ForwardedIpTrustFilter(List.of(cidr)).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> assertThat(req.getAttribute(ForwardedIpTrustFilter.CLIENT_IP_ATTRIBUTE)).isEqualTo(expected));
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext());
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    @Configuration
    static class TestInfrastructure {
        @Bean ProxyManager<byte[]> proxyManager() { return org.mockito.Mockito.mock(ProxyManager.class); }
    }
}
