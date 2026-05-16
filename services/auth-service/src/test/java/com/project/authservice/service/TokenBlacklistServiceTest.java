package com.project.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(redisTemplate);
        ReflectionTestUtils.setField(tokenBlacklistService, "accessTokenExpiration", 900000L);
    }

    @Test
    void blacklistToken_SetsValueInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        tokenBlacklistService.blacklistToken("test-token");

        verify(valueOps).set("blacklist:token:test-token", "blacklisted", 900000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void isBlacklisted_WhenExists_ReturnsTrue() {
        when(redisTemplate.hasKey("blacklist:token:test-token")).thenReturn(true);

        boolean result = tokenBlacklistService.isBlacklisted("test-token");

        assertThat(result).isTrue();
    }

    @Test
    void isBlacklisted_WhenNotExists_ReturnsFalse() {
        when(redisTemplate.hasKey("blacklist:token:test-token")).thenReturn(false);

        boolean result = tokenBlacklistService.isBlacklisted("test-token");

        assertThat(result).isFalse();
    }
}
