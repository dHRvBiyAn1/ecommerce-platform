package com.project.authservice.controller;

import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
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
        ServiceTokenResponse token = new ServiceTokenResponse(
                "signed-service-token", "Bearer", 300L, "inventory.reserve");
        when(clientCredentialsService.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.reserve"))).thenReturn(token);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        var response = controller.token(
                "client_credentials", null, null,
                "order-service", "correct-secret", "inventory.reserve",
                new MockHttpServletRequest(), servletResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(token);
        assertThat(servletResponse.getHeader("Set-Cookie")).isNull();
        verify(clientCredentialsService).issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.reserve"));
        verifyNoInteractions(authService);
    }
}
