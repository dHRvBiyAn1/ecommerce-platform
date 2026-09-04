package com.project.inventory.domain.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class InsufficientStockExceptionTest {
    @Test
    void isAStableConflictBusinessError() {
        InsufficientStockException exception = new InsufficientStockException("not enough stock");
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_STOCK");
    }
}
