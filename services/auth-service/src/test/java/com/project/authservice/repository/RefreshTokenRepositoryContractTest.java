package com.project.authservice.repository;

import com.project.authservice.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryContractTest {

    @Test
    void refreshLookupUsesPessimisticWriteLock() throws Exception {
        Method method = RefreshTokenRepository.class
                .getMethod("findForUpdateByTokenHash", String.class);

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
