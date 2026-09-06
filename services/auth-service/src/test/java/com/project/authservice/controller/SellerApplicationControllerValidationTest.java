package com.project.authservice.controller;

import com.project.authservice.exception.AuthException;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.SellerApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SellerApplicationControllerValidationTest {

    private final SellerApplicationService service = mock(SellerApplicationService.class);
    private final AuthenticatedUserValidator validator = new AuthenticatedUserValidator();

    @Test
    void malformedCustomerPrincipalFailsBeforeGetMineService() {
        SellerApplicationController controller = new SellerApplicationController(service, validator);

        assertThatThrownBy(() -> controller.getMine(authentication("not-a-uuid")))
                .isInstanceOf(AuthException.class);

        verifyNoInteractions(service);
    }

    @Test
    void malformedAdminPrincipalFailsBeforeApproveService() {
        AdminSellerApplicationController controller = new AdminSellerApplicationController(service, validator);

        assertThatThrownBy(() -> controller.approve(java.util.UUID.randomUUID(), authentication("not-a-uuid")))
                .isInstanceOf(AuthException.class);

        verifyNoInteractions(service);
    }

    private UsernamePasswordAuthenticationToken authentication(String name) {
        return new UsernamePasswordAuthenticationToken(name, null);
    }
}
