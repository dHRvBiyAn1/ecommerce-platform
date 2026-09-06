package com.project.authservice.service;

import com.project.authservice.exception.TokenRefreshException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTransactionContractTest {

    @Test
    void refreshFailuresCommitReuseRevocation() throws Exception {
        Method method = AuthService.class.getMethod(
                "authenticate", String.class, String.class, String.class,
                String.class, String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.dontRollbackOn()).contains(TokenRefreshException.class);
    }
}
