package com.project.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAuthContextTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FeignAuthForwardingConfig.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("invalidConfiguration")
    void refusesStartupInsteadOfForwardingWhenConfigurationIsIncomplete(String[] properties) {
        runner.withPropertyValues(properties).run(context -> assertThat(context).hasFailed());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidConfiguration() {
        List<String[]> cases = new ArrayList<>();
        String[] fields = {"token-uri", "client-id", "client-secret", "scope"};
        String[] values = {"http://localhost/token", "order-service", "test-secret", "inventory.write"};
        for (int missing = 0; missing < fields.length; missing++) {
            for (String invalid : new String[]{null, "", "   "}) {
                List<String> properties = new ArrayList<>(List.of("service.auth.enabled=true"));
                for (int i = 0; i < fields.length; i++) {
                    if (i != missing || invalid != null) {
                        properties.add("service.auth." + fields[i] + "=" + (i == missing ? invalid : values[i]));
                    }
                }
                cases.add(properties.toArray(String[]::new));
            }
            cases.add(new String[]{"service.auth." + fields[missing] + "=" + values[missing]});
            cases.add(new String[]{"service.auth." + fields[missing] + "=   "});
        }
        return cases.stream().map(properties -> org.junit.jupiter.params.provider.Arguments.of((Object) properties));
    }

    @ParameterizedTest
    @ValueSource(strings = {"order-service", "payment-service", "cart-service"})
    void requiredCallersCannotStartWithAbsentOrDisabledServiceAuth(String applicationName) {
        runner.withPropertyValues("spring.application.name=" + applicationName)
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.application.name=" + applicationName, "service.auth.enabled=false",
                        "service.auth.token-uri=http://localhost/token", "service.auth.client-id=" + applicationName,
                        "service.auth.client-secret=test-secret", "service.auth.scope=coupons.read")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void replacesExistingAuthorizationWithExactlyOneMachineBearer() {
        userContext();
        enabledRunner(properties -> new ServiceTokenResponse("machine-token", "Bearer", 300, "coupons.read"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RequestTemplate template = new RequestTemplate();
                    template.header("Authorization", "Bearer stale-token", "Basic stale-credentials");
                    context.getBean(RequestInterceptor.class).apply(template);
                    assertThat(template.headers().get("Authorization")).containsExactly("Bearer machine-token");
                });
    }

    @Test
    void tokenExchangeFailureAbortsInsteadOfFallingBackToUserBearer() {
        userContext();
        enabledRunner(properties -> { throw new IllegalStateException("exchange unavailable"); })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RequestTemplate template = new RequestTemplate();
                    assertThatThrownBy(() -> context.getBean(RequestInterceptor.class).apply(template))
                            .isInstanceOf(IllegalStateException.class);
                    assertThat(template.headers()).doesNotContainKey("Authorization");
                });
    }

    @Test
    void genuinelyUnconfiguredOtherConsumerRetainsExistingUserForwarding() {
        userContext();
        runner.withPropertyValues("spring.application.name=other-consumer").run(context -> {
            assertThat(context).hasNotFailed();
            RequestTemplate template = new RequestTemplate();
            context.getBean(RequestInterceptor.class).apply(template);
            assertThat(template.headers().get("Authorization")).containsExactly("Bearer user-token");
        });
    }

    private ApplicationContextRunner enabledRunner(ServiceTokenClient client) {
        ServiceAuthProperties properties = new ServiceAuthProperties();
        properties.setScope("coupons.read");
        return runner.withPropertyValues("service.auth.enabled=true", "service.auth.token-uri=http://localhost/token",
                        "service.auth.client-id=cart-service", "service.auth.client-secret=test-secret",
                        "service.auth.scope=coupons.read")
                .withBean("testTokenProvider", ServiceTokenProvider.class,
                        () -> new ServiceTokenProvider(client, properties), definition -> definition.setPrimary(true));
    }

    private void userContext() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("user-token").header("alg", "RS256").subject("user").build()));
    }
}
