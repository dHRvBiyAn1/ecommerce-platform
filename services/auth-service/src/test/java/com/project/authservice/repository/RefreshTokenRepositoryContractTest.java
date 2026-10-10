package com.project.authservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class RefreshTokenRepositoryContractTest {

  @Test
  void refreshLookupUsesPessimisticWriteLock() throws Exception {
    Method method =
        RefreshTokenRepository.class.getMethod("findForUpdateByTokenHash", String.class);

    assertThat(method.getReturnType()).isEqualTo(Optional.class);
    assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }
}
