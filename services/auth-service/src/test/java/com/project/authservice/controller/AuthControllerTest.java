package com.project.authservice.controller;

import com.project.authservice.generated.model.ServiceTokenResponse;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import com.project.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private ClientCredentialsService clientCredentialsService;

    @InjectMocks
    private AuthController controller;

    @Test
    void clientCredentialsGrantReturnsOAuthFieldsWithoutRefreshCookie() {
        ReflectionTestUtils.setField(controller, "refreshTokenDurationMs", 604_800_000L);
        ReflectionTestUtils.setField(controller, "secureCookie", true);
        ServiceTokenResponse token = new ServiceTokenResponse().accessToken("signed-service-token")
                .tokenType(ServiceTokenResponse.TokenTypeEnum.BEARER).expiresIn(300L).scope("inventory.write");
        when(clientCredentialsService.issue("order-service", "correct-secret", "inventory.write")).thenReturn(token);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        var response = controller.token(
                "client_credentials", null, null,
                "order-service", "correct-secret", "inventory.write",
                new MockHttpServletRequest(), servletResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(token);
        assertThat(servletResponse.getHeader("Set-Cookie")).isNull();
        verify(clientCredentialsService).issue("order-service", "correct-secret", "inventory.write");
        verifyNoInteractions(authService);
    }

    @Test
    void unrelatedBusinessFailureIsNotClassifiedAsInvalidClient() {
        when(clientCredentialsService.issue("order-service", "correct-secret", "inventory.write"))
                .thenThrow(new DuplicateResourceException("client state conflict"));

        assertThatThrownBy(() -> controller.token(
                "client_credentials", null, null,
                "order-service", "correct-secret", "inventory.write",
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isExactlyInstanceOf(DuplicateResourceException.class)
                .hasMessage("client state conflict");
    }
}
